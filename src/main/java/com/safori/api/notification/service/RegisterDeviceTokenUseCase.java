package com.safori.api.notification.service;

import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.common.annotation.UseCase;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class RegisterDeviceTokenUseCase {

    private final UserAdaptor userAdaptor;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 디바이스 토큰 등록 (upsert).
     * 동일 토큰이 이미 존재하면 소유자만 갱신한다 — 기기 양도/재로그인 시 다른 사용자로 재등록되는 케이스.
     */
    @Transactional
    public Long execute(String username, DeviceTokenRegisterRequest request) {
        User user = userAdaptor.queryUserByUsername(username);

        DeviceToken deviceToken = deviceTokenRepository.findByToken(request.getToken())
                .map(existing -> {
                    existing.reassignTo(user);
                    return existing;
                })
                .orElseGet(() -> deviceTokenRepository.save(
                        DeviceToken.builder()
                                .user(user)
                                .token(request.getToken())
                                .build()));

        return deviceToken.getId();
    }
}
