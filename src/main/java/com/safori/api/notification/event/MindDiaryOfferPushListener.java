package com.safori.api.notification.event;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.service.SendPushNotificationUseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.common.event.MindDiaryOfferedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 마음일기 상담 제안(OFFERED)이 커밋되면 대상 사용자에게 푸시를 보낸다.
 *
 * <p>{@code AFTER_COMMIT} 로 소비하므로, 중복 제안으로 {@code recordOffer} 트랜잭션이
 * 롤백되면 이 리스너는 실행되지 않는다 — 거짓 알림 방지. 전송 실패는 삼키고(로그만) 상담
 * 흐름에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiaryOfferPushListener {

    private final SendPushNotificationUseCase sendPushNotificationUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMindDiaryOffered(MindDiaryOfferedEvent event) {
        try {
            PushMessage message = new PushMessage(
                    NotificationStaticValues.MIND_DIARY_OFFER_TITLE,
                    NotificationStaticValues.MIND_DIARY_OFFER_BODY,
                    Map.of(
                            NotificationStaticValues.DATA_TYPE, NotificationStaticValues.TYPE_MIND_DIARY_OFFER,
                            NotificationStaticValues.KEY_OFFER_ID, String.valueOf(event.offerId())
                    ));
            sendPushNotificationUseCase.execute(event.userId(), message);
        } catch (Exception e) {
            log.error("마음일기 제안 푸시 실패 (silent) — userId={}, offerId={}",
                    event.userId(), event.offerId(), e);
        }
    }
}
