package com.safori.domain.notification.service;

import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.user.entity.User;

public interface DeviceTokenDomainService {

    /**
     * 디바이스 토큰 등록 (upsert).
     * 동일 토큰이 이미 존재하면 소유자만 갱신한다 — 기기 양도/재로그인 시 다른 사용자로 재등록되는 케이스.
     */
    DeviceToken registerToken(User user, String token);

    /**
     * 디바이스 토큰 삭제.
     * 본인 소유 토큰만 삭제하며, 없거나 타인 소유면 무시한다 (멱등).
     */
    void deleteToken(User user, String token);
}
