package com.safori.api.notification.service;

import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.service.DeviceTokenDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RegisterDeviceTokenUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock DeviceTokenDomainService deviceTokenDomainService;
    @InjectMocks RegisterDeviceTokenUseCase registerDeviceTokenUseCase;

    @Test
    @DisplayName("토큰 등록 - 인증 사용자를 조회해 도메인 서비스에 위임하고 deviceTokenId를 반환한다")
    void execute_delegatesToDomainService() {
        User user = User.builder().id(1L).username("user01").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(deviceTokenDomainService.registerToken(user, "fcm-token"))
                .willReturn(DeviceToken.builder().id(10L).user(user).token("fcm-token").build());

        Long id = registerDeviceTokenUseCase.execute("user01",
                DeviceTokenRegisterRequest.builder().token("fcm-token").build());

        assertThat(id).isEqualTo(10L);
    }
}
