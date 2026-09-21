package com.safori.api.notification.port;

import java.util.List;

/**
 * 푸시 전송 결과. invalidTokens 는 재사용 불가 토큰(UNREGISTERED 등) — 호출측에서 DB 정리에 사용.
 */
public record PushSendResult(int successCount, int failureCount, List<String> invalidTokens) {

    public static PushSendResult empty() {
        return new PushSendResult(0, 0, List.of());
    }
}
