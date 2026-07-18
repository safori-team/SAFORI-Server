package com.safori.domain.chatbot.event;

import com.safori.common.event.VoiceReanalyzedEvent;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 재분석으로 감정이 바뀌면, 조건이 깨진 상담 제안/0턴 세션을 철회한다.
 *
 * <p>예: 수·목·금 부정 → 금요일에 상담 제안(OFFERED). 사용자가 "목요일은 슬픔 아니라 기쁨"으로
 * 신고 → 재분석 → 3일 연속이 깨짐 → 이 리스너가 금요일 제안을 철회한다.
 * 이미 대화가 오간(발화≥1) 세션은 건드리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiaryReevalListener {

    private final ChatbotDomainService chatbotDomainService;

    @Async
    @EventListener
    public void onVoiceReanalyzed(VoiceReanalyzedEvent event) {
        try {
            chatbotDomainService.withdrawStaleTriggers(event.userId());
        } catch (Exception e) {
            log.error("재분석 후 트리거 재평가 실패 — userId={}, voiceId={} (silent fail)",
                    event.userId(), event.voiceId(), e);
        }
    }
}
