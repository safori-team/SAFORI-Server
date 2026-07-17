package com.safori.api.notification.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class DeleteDeviceTokenUseCase {

    private final UserAdaptor userAdaptor;
    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 디바이스 토큰 삭제 (로그아웃 시).
     * 본인 소유 토큰만 삭제하며, 없거나 타인 소유면 무시한다 (멱등).
     */
    @Transactional
    public void execute(String username, String token) {
        User user = userAdaptor.queryUserByUsername(username);

        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUser().getId().equals(user.getId()))
                .ifPresent(deviceTokenRepository::delete);
    }
}
