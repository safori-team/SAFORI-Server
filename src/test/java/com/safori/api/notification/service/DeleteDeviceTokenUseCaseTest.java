package com.safori.api.notification.service;

import com.safori.domain.notification.service.DeviceTokenDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DeleteDeviceTokenUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock DeviceTokenDomainService deviceTokenDomainService;
    @InjectMocks DeleteDeviceTokenUseCase deleteDeviceTokenUseCase;

    @Test
    @DisplayName("토큰 삭제 - 인증 사용자를 조회해 도메인 서비스에 위임한다")
    void execute_delegatesToDomainService() {
        User user = User.builder().id(1L).username("user01").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        deleteDeviceTokenUseCase.execute("user01", "fcm-token");

        then(deviceTokenDomainService).should().deleteToken(user, "fcm-token");
    }
}
