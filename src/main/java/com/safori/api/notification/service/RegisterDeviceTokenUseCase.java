package com.safori.api.notification.service;

import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.common.annotation.UseCase;
import com.safori.domain.notification.service.DeviceTokenDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class RegisterDeviceTokenUseCase {

    private final UserAdaptor userAdaptor;
    private final DeviceTokenDomainService deviceTokenDomainService;

    public Long execute(String username, DeviceTokenRegisterRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        return deviceTokenDomainService.registerToken(user, request.getToken()).getId();
    }
}
