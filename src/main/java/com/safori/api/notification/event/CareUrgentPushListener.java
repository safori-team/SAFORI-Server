package com.safori.api.notification.event;

import com.safori.api.notification.service.SendCarePushUseCase;
import com.safori.common.event.CareUrgentEnteredEvent;
import com.safori.domain.notification.entity.CareNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대상자가 즉시 확인으로 올라가면 기관 관리자·담당자·보호자에게 바로 푸시를 보낸다.
 *
 * <p>{@code AFTER_COMMIT}으로 소비하므로 기록 저장이 롤백되면 푸시가 나가지 않는다.
 * 전송 실패는 삼키고(로그만) 기록을 올린 요청(긴급 전화 등)에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareUrgentPushListener {

    private final SendCarePushUseCase sendCarePushUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUrgentEntered(CareUrgentEnteredEvent event) {
        try {
            sendCarePushUseCase.execute(event.recordId(), CareNotificationType.URGENT_ENTERED);
        } catch (Exception e) {
            log.error("즉시 확인 푸시 실패 (silent) — recordId={}", event.recordId(), e);
        }
    }
}
