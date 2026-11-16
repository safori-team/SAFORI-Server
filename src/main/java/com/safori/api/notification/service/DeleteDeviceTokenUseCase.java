package com.safori.api.notification.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.notification.service.DeviceTokenDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class DeleteDeviceTokenUseCase {

    private final UserAdaptor userAdaptor;
    private final DeviceTokenDomainService deviceTokenDomainService;

    public void execute(String username, String token) {
        User user = userAdaptor.queryUserByUsername(username);
        deviceTokenDomainService.deleteToken(user, token);
    }

    /** 백오피스 계정(관리자·담당자·보호자)의 토큰 삭제. */
    public void executeForAccount(Long accountId, String token) {
        deviceTokenDomainService.deleteAccountToken(accountId, token);
    }
}
