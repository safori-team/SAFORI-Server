package com.safori.infra.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.safori.api.notification.port.PushMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * FCM SDK 타입 ↔ 도메인 메시지 변환 헬퍼. Firebase 의존을 infra 안에 가둔다.
 */
@UtilityClass
class FcmMessageMapper {

    MulticastMessage toMulticastMessage(List<String> tokens, PushMessage message) {
        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
    }

    /** UNREGISTERED(앱 삭제·토큰 만료), INVALID_ARGUMENT(형식 오류) 토큰을 재사용 불가로 수집 */
    List<String> extractInvalidTokens(List<String> tokens, BatchResponse response) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = sendResponse.getException().getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(tokens.get(i));
            }
        }
        return invalidTokens;
    }
}
