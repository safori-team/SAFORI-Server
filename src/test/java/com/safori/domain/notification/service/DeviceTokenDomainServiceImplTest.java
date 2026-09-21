package com.safori.domain.notification.service;

import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DeviceTokenDomainServiceImplTest {

    @Mock DeviceTokenRepository deviceTokenRepository;
    @InjectMocks DeviceTokenDomainServiceImpl deviceTokenDomainService;

    @Test
    @DisplayName("토큰 등록 - 신규 토큰이면 저장한다")
    void registerToken_savesNewToken() {
        User user = User.builder().id(1L).username("user01").build();
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.empty());
        given(deviceTokenRepository.save(any(DeviceToken.class)))
                .willReturn(DeviceToken.builder().id(10L).user(user).token("fcm-token").build());

        DeviceToken result = deviceTokenDomainService.registerToken(user, "fcm-token");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("토큰 등록 - 동일 토큰이 이미 있으면 저장 없이 소유자만 갱신한다(upsert)")
    void registerToken_reassignsExistingToken() {
        User oldOwner = User.builder().id(1L).username("user01").build();
        User newOwner = User.builder().id(2L).username("user02").build();
        DeviceToken existing = DeviceToken.builder().id(10L).user(oldOwner).token("fcm-token").build();
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(existing));

        DeviceToken result = deviceTokenDomainService.registerToken(newOwner, "fcm-token");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(existing.getUser()).isEqualTo(newOwner);
        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("토큰 삭제 - 본인 소유 토큰이면 삭제한다")
    void deleteToken_deletesOwnToken() {
        User user = User.builder().id(1L).username("user01").build();
        DeviceToken deviceToken = DeviceToken.builder().id(10L).user(user).token("fcm-token").build();
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(deviceToken));

        deviceTokenDomainService.deleteToken(user, "fcm-token");

        then(deviceTokenRepository).should().delete(deviceToken);
    }

    @Test
    @DisplayName("토큰 삭제 - 타인 소유 토큰이면 삭제하지 않는다")
    void deleteToken_ignoresOthersToken() {
        User owner = User.builder().id(1L).username("user01").build();
        User requester = User.builder().id(2L).username("user02").build();
        DeviceToken deviceToken = DeviceToken.builder().id(10L).user(owner).token("fcm-token").build();
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.of(deviceToken));

        deviceTokenDomainService.deleteToken(requester, "fcm-token");

        then(deviceTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("토큰 삭제 - 없는 토큰이면 무시한다(멱등)")
    void deleteToken_ignoresMissingToken() {
        User user = User.builder().id(1L).username("user01").build();
        given(deviceTokenRepository.findByToken("fcm-token")).willReturn(Optional.empty());

        deviceTokenDomainService.deleteToken(user, "fcm-token");

        then(deviceTokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("무효 토큰 일괄 삭제 - 토큰 목록으로 삭제한다")
    void deleteByTokens_deletesAll() {
        deviceTokenDomainService.deleteByTokens(List.of("token-a", "token-b"));

        then(deviceTokenRepository).should().deleteAllByTokenIn(List.of("token-a", "token-b"));
    }

    @Test
    @DisplayName("무효 토큰 일괄 삭제 - 빈 목록이면 아무것도 하지 않는다")
    void deleteByTokens_noOpWhenEmpty() {
        deviceTokenDomainService.deleteByTokens(List.of());

        then(deviceTokenRepository).should(never()).deleteAllByTokenIn(any());
    }
}
