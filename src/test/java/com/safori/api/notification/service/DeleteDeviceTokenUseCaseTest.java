package com.safori.api.notification.service;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DeleteDeviceTokenUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock DeviceTokenRepository deviceTokenRepository;
    @InjectMocks DeleteDeviceTokenUseCase deleteDeviceTokenUseCase;

    @Test
    @DisplayName("토큰 삭제 - 본인 소유 토큰이면 삭제한다")
    void execute_deletesOwnToken() {
        User user = User.builder().id(1L).username("user01").build();
        DeviceToken deviceToken = DeviceToken.builder().id(10L).user(user).token("fcm-token").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(deviceToken));

        deleteDeviceTokenUseCase.execute("user01", "fcm-token");

        then(deviceTokenRepository).should().delete(deviceToken);
    }

    @Test
    @DisplayName("토큰 삭제 - 타인 소유 토큰이면 삭제하지 않는다")
    void execute_ignoresOthersToken() {
        User owner = User.builder().id(1L).username("user01").build();
        User requester = User.builder().id(2L).username("user02").build();
        DeviceToken deviceToken = DeviceToken.builder().id(10L).user(owner).token("fcm-token").build();
        given(userAdaptor.queryUserByUsername("user02")).willReturn(requester);
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(deviceToken));

        deleteDeviceTokenUseCase.execute("user02", "fcm-token");

        then(deviceTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("토큰 삭제 - 없는 토큰이면 무시한다(멱등)")
    void execute_ignoresMissingToken() {
        User user = User.builder().id(1L).username("user01").build();
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.empty());

        deleteDeviceTokenUseCase.execute("user01", "fcm-token");

        then(deviceTokenRepository).should(never()).delete(any());
    }
}
