package com.safori.api.notification.service;

import com.safori.api.notification.dto.DeviceTokenRegisterRequest;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RegisterDeviceTokenUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock DeviceTokenRepository deviceTokenRepository;
    @InjectMocks RegisterDeviceTokenUseCase registerDeviceTokenUseCase;

    @Test
    @DisplayName("토큰 등록 - 신규 토큰이면 저장한다")
    void execute_savesNewToken() {
        User user = User.builder().id(1L).username("user01").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.empty());
        given(deviceTokenRepository.save(any(DeviceToken.class)))
                .willReturn(DeviceToken.builder().id(10L).user(user).token("fcm-token").build());

        Long id = registerDeviceTokenUseCase.execute("user01",
                DeviceTokenRegisterRequest.builder().token("fcm-token").build());

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("토큰 등록 - 동일 토큰이 이미 있으면 저장 없이 소유자만 갱신한다(upsert)")
    void execute_reassignsExistingToken() {
        User oldOwner = User.builder().id(1L).username("user01").build();
        User newOwner = User.builder().id(2L).username("user02").build();
        DeviceToken existing = DeviceToken.builder().id(10L).user(oldOwner).token("fcm-token").build();
        given(userAdaptor.queryUserByUsername("user02")).willReturn(newOwner);
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(existing));

        Long id = registerDeviceTokenUseCase.execute("user02",
                DeviceTokenRegisterRequest.builder().token("fcm-token").build());

        assertThat(id).isEqualTo(10L);
        assertThat(existing.getUser()).isEqualTo(newOwner);
        then(deviceTokenRepository).should(never()).save(any());
    }
}
