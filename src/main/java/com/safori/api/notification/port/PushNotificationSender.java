package com.safori.api.notification.port;

import java.util.List;

/**
 * 푸시 전송 포트. 애플리케이션(UseCase)이 FCM 등 구체 구현에 의존하지 않도록 분리한다.
 * 구현체: infra/fcm/FcmPushNotificationSender (infra → api 방향, 클린 아키텍처 정방향)
 */
public interface PushNotificationSender {

    /**
     * 주어진 디바이스 토큰들로 푸시를 전송한다.
     * 전송 실패는 예외로 전파하지 않고 결과에 집계한다 — 알림 실패가 본 흐름을 깨지 않도록.
     */
    PushSendResult send(List<String> tokens, PushMessage message);
}
