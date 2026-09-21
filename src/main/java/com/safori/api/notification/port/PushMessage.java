package com.safori.api.notification.port;

import java.util.Map;

/**
 * 푸시 알림 메시지. title/body 는 노출 문구, data 는 클라이언트 라우팅용 페이로드(딥링크 등).
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public static PushMessage of(String title, String body) {
        return new PushMessage(title, body, Map.of());
    }
}
