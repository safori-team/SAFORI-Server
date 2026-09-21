package com.safori.domain.chatbot.scheduler;

import com.safori.domain.chatbot.policy.DiaryEmotionHistoryPort;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.policy.SessionTriggerDecision;
import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마음일기 → 상담 제안(OFFERED)을 주기적으로 감지한다.
 *
 * <p>이 스케줄러는 <b>조건 충족을 감지</b>만 한다. 세션 생성과 LLM 호출은 사용자가 모달에서
 * 상담을 수락할 때 별도 경로(AcceptMindDiaryOffer)에서 일어난다. 거절될 수도 있는 상담에
 * 미리 토큰을 쓰지 않기 위함이다.
 *
 * <p>voice 테이블을 직접 훑으므로:
 * <ul>
 *   <li>재분석처럼 이벤트를 발행하지 않는 흐름도 자연히 포함된다</li>
 *   <li>사용자가 뒤늦게 감정을 신고해도 다음 주기에 재평가된다</li>
 *   <li>정책을 바꾸면 스캔 범위 안의 과거 일기가 새 기준으로 다시 평가된다</li>
 * </ul>
 *
 * <p>재제안은 {@code mind_diary_trigger} 원장으로 막는다. 원장에 행이 있으면(OFFERED/ACCEPTED/
 * DECLINED 무엇이든) 스캔에서 제외된다. 인스턴스를 늘리면 두 인스턴스가 같은 후보에 동시에
 * 제안을 넣을 수 있는데(voice_id UNIQUE가 하나만 통과시킴, 정합성 유지), 그때 {@link #run()}에
 * ShedLock의 {@code @SchedulerLock}을 붙이면 된다.
 *
 * <p>조건 판단 기준은 이 클래스에 없다 — {@link MindDiaryTriggerEvaluator}에 위임한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiarySessionScheduler {

    private final SessionTriggerProperties props;
    private final DiaryEmotionHistoryPort historyPort;
    private final MindDiaryTriggerEvaluator evaluator;
    private final ChatbotDomainService chatbotDomainService;

    @Scheduled(
            initialDelayString = "${safori.chatbot.session-trigger.interval:PT10M}",
            fixedDelayString = "${safori.chatbot.session-trigger.interval:PT10M}")
    public void run() {
        LocalDateTime since = LocalDateTime.now().minus(props.getScanLookback());
        List<Long> candidates =
                historyPort.findUntriggeredVoiceIds(since, props.getScanBatchSize());
        if (candidates.isEmpty()) return;

        log.info("마음일기 제안 스캔 시작 — 후보 {}건, 정책={}", candidates.size(), props.getPolicy());
        int offered = 0;
        for (Long voiceId : candidates) {
            try {
                if (offerIfEligible(voiceId)) offered++;
            } catch (Exception e) {
                // 후보 하나가 실패해도 나머지는 처리한다. 다음 주기에 재시도된다.
                log.error("마음일기 제안 생성 실패 — voiceId={}", voiceId, e);
            }
        }
        log.info("마음일기 제안 스캔 완료 — 후보 {}건 중 {}건 제안", candidates.size(), offered);
    }

    /** @return 제안을 실제로 기록했으면 true */
    private boolean offerIfEligible(Long voiceId) {
        MindDiaryTriggerEvaluator.Evaluation eval = evaluator.evaluate(voiceId);
        SessionTriggerDecision decision = eval.decision();
        if (!decision.create()) {
            log.debug("제안 보류 — voiceId={}, reason={}", voiceId, decision.reasonCode());
            return false;
        }
        try {
            chatbotDomainService.recordOffer(eval.voice(), decision.reasonCode());
            log.info("마음일기 상담 제안 — voiceId={}, user={}, reason={}",
                    voiceId, eval.user().getUsername(), decision.reasonCode());
            return true;
        } catch (DataIntegrityViolationException dup) {
            // 원장 voice_id UNIQUE 위반 = 다른 인스턴스가 같은 일기로 먼저 제안. 정상 경합.
            log.info("마음일기 제안 중복 감지 — 건너뜀. voiceId={}", voiceId);
            return false;
        }
    }
}
