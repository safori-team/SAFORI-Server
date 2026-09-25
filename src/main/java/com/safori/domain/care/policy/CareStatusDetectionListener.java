package com.safori.domain.care.policy;

import com.safori.common.event.CounselExtendedEvent;
import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.common.event.VoiceReanalyzedEvent;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 판정 시점. 일기 분석 완료·재분석(동일 감정 반복), 상담 연장(추가 상담 반복 이용)은 이벤트로,
 * 작성 주기 감소는 쓰지 않은 것을 잡아야 하므로 매일 배치로 판정한다.
 * 판정 실패가 원래 흐름(분석·상담)을 깨지 않도록 비동기로 돌리고 실패는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareStatusDetectionListener {

    private final CareStatusDetector detector;
    private final VoiceRepository voiceRepository;

    /** 분석 스레드에서 저장 커밋 이후(트랜잭션 밖) 발행된다. */
    @Async
    @EventListener
    public void onAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        voiceRepository.findById(event.voiceId())
                .map(voice -> voice.getUser().getId())
                .ifPresent(this::evaluateSameEmotion);
    }

    @Async
    @EventListener
    public void onReanalyzed(VoiceReanalyzedEvent event) {
        evaluateSameEmotion(event.userId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCounselExtended(CounselExtendedEvent event) {
        try {
            detector.evaluateCounselExtension(event.userId(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("추가 상담 반복 판정 실패 — userId={}", event.userId(), e);
        }
    }

    /** 매일 오전 9시(담당자 업무 시작 전). 대상자 하나가 실패해도 나머지는 판정한다. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void evaluateDiaryIntervals() {
        LocalDateTime now = LocalDateTime.now();
        detector.intervalTargets().forEach(recipient -> {
            try {
                detector.evaluateDiaryInterval(recipient, now);
            } catch (Exception e) {
                log.error("작성 주기 감소 판정 실패 — recipientId={}", recipient.getId(), e);
            }
        });
    }

    private void evaluateSameEmotion(Long userId) {
        try {
            detector.evaluateSameEmotion(userId, LocalDateTime.now());
        } catch (Exception e) {
            log.error("동일 감정 반복 판정 실패 — userId={}", userId, e);
        }
    }
}
