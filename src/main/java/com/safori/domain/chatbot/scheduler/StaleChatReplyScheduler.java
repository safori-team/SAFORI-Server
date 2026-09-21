package com.safori.domain.chatbot.scheduler;

import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 응답을 만들지 못한 채 PROCESSING으로 남은 메시지를 FAILED로 마감한다.
 *
 * <p>서버가 LLM 호출 도중 죽거나 배포로 재시작되면 {@code settleMessage}가 실행되지 않아
 * 행이 영원히 PROCESSING으로 남는다. 그러면 그 세션은 조회할 때마다 "처리 중"으로 보이고,
 * 재전송도 {@code 4211}로 계속 막혀 사용자가 대화를 이어갈 수 없다.
 *
 * <p>폴백 응답으로 마감하므로 앱은 실패 말풍선 + 재시도 버튼을 띄울 수 있고, 실패한 턴은
 * 대화 턴 한도를 소모하지 않아 같은 내용으로 다시 보낼 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleChatReplyScheduler {

    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    /** 이 시간을 넘긴 PROCESSING은 실패로 본다. 음성 최악 케이스(20초)보다 넉넉하게 잡는다. */
    @Value("${safori.chatbot.reply-timeout:PT5M}")
    private Duration replyTimeout;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void run() {
        List<ChatMessage> stale =
                chatMessageAdaptor.queryStaleProcessing(LocalDateTime.now().minus(replyTimeout));
        if (stale.isEmpty()) return;

        log.warn("응답 미확정 메시지 {}건 발견 — FAILED로 마감한다", stale.size());
        for (ChatMessage m : stale) {
            try {
                // 폴백 응답을 채워 넣는다 — 앱이 빈 말풍선을 만나지 않도록
                chatbotDomainService.settleMessage(m.getId(), ChatbotReply.fallback(), true);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 처리한다. 다음 주기에 재시도된다.
                log.error("응답 미확정 메시지 마감 실패 — messageId={}", m.getId(), e);
            }
        }
    }
}
