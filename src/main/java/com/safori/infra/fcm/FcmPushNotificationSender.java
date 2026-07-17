package com.safori.infra.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushNotificationSender;
import com.safori.api.notification.port.PushSendResult;
import com.safori.common.consts.FcmStaticValues;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * FCM 푸시 전송 어댑터.
 * FirebaseMessaging 빈이 없으면(FIREBASE_CREDENTIALS_BASE64 미설정) 전송을 건너뛴다 — 서버는 정상 동작.
 * 전송 실패는 예외로 전파하지 않고 결과에 집계한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmPushNotificationSender implements PushNotificationSender {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Override
    public PushSendResult send(List<String> tokens, PushMessage message) {
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null) {
            log.debug("FirebaseMessaging 미설정 — 푸시 전송 건너뜀 (tokens={})", tokens.size());
            return PushSendResult.empty();
        }
        if (tokens.isEmpty()) {
            return PushSendResult.empty();
        }

        int successCount = 0;
        int failureCount = 0;
        List<String> invalidTokens = new ArrayList<>();

        for (int start = 0; start < tokens.size(); start += FcmStaticValues.MULTICAST_BATCH_SIZE) {
            List<String> batch = tokens.subList(
                    start, Math.min(start + FcmStaticValues.MULTICAST_BATCH_SIZE, tokens.size()));
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(
                        FcmMessageMapper.toMulticastMessage(batch, message));
                successCount += response.getSuccessCount();
                failureCount += response.getFailureCount();
                invalidTokens.addAll(FcmMessageMapper.extractInvalidTokens(batch, response));
            } catch (FirebaseMessagingException e) {
                failureCount += batch.size();
                log.error("FCM 멀티캐스트 전송 실패 (tokens={}): {}", batch.size(), e.getMessage(), e);
            }
        }

        if (failureCount > 0) {
            log.warn("FCM 전송 완료 — success={}, failure={}, invalidTokens={}",
                    successCount, failureCount, invalidTokens.size());
        }
        return new PushSendResult(successCount, failureCount, List.copyOf(invalidTokens));
    }
}
