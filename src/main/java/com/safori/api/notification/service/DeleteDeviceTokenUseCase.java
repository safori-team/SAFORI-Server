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
}
