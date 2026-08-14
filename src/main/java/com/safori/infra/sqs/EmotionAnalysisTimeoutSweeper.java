package com.safori.infra.sqs;

import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.domain.emotion.adaptor.EmotionAnalysisRequestAdaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.infra.sqs.config.EmotionAnalysisSqsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 응답이 오지 않은 소분류 분석 요청을 마감한다.
 *
 * <p>분석 Lambda 5xx·호출 실패·타임아웃은 요청 큐에서 재시도되고 <b>응답 큐에는 아무 것도
 * 들어오지 않는다.</b> 이 스윕이 없으면 해당 일기는 영원히 분석 중으로 남아 완료 푸시도,
 * 상담 세션 트리거도 오지 않는다.
 *
 * <p>마감된 일기는 Gemini 소분류를 그대로 유지한 채 완료 처리된다. 이후 늦게 도착한 응답은
 * 최종 상태 확인에 걸려 중복으로 폐기되므로 라벨이 뒤늦게 뒤바뀌지 않는다.
 *
 * <p>파이프라인을 꺼도 남아 있는 PENDING을 회수해야 하므로 큐 설정과 무관하게 항상 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisTimeoutSweeper {

    private final EmotionAnalysisSqsProperties properties;
    private final EmotionAnalysisRequestAdaptor requestAdaptor;
    private final EmotionAnalysisResultApplier resultApplier;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(
            initialDelayString = "${safori.emotion-analysis.sweep-interval:PT2M}",
            fixedDelayString = "${safori.emotion-analysis.sweep-interval:PT2M}")
    public void run() {
        LocalDateTime threshold = LocalDateTime.now().minus(properties.getPendingTimeout());
        List<EmotionAnalysisRequest> stale = requestAdaptor.queryPendingCreatedBefore(threshold);
        if (stale.isEmpty()) return;

        log.warn("소분류 감정 분석 응답 미도착 {}건 — Gemini 소분류로 마감", stale.size());
        for (EmotionAnalysisRequest request : stale) {
            String requestId = request.getRequestId();
            try {
                resultApplier.timeOut(requestId).ifPresent(voiceId -> {
                    log.warn("소분류 감정 분석 타임아웃 마감 — requestId={}, voiceId={}", requestId, voiceId);
                    publishCompleted(requestId, voiceId);
                });
            } catch (Exception e) {
                // 한 건이 실패해도 나머지는 마감한다. 다음 주기에 재시도된다.
                log.error("소분류 감정 분석 타임아웃 마감 실패 — requestId={}", requestId, e);
            }
        }
    }

    private void publishCompleted(String requestId, Long voiceId) {
        try {
            eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voiceId));
        } catch (Exception e) {
            log.warn("VoiceAnalysisCompletedEvent 발행 실패 (마감은 완료됨) — requestId={}", requestId, e);
        }
    }
}
