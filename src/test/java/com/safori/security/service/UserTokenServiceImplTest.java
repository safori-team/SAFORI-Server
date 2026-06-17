package com.safori.security.service;

import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.exception.UserHandler;
import com.safori.security.dto.JwtToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceImplTest {

    private static final String SECRET =
            "ZHVtbXktc2VjcmV0LWZvci10ZXN0LW9ubHktc2Fmb3JpLTIwMjYtand0LWtleQ==";
    private static final String RAW_PASSWORD = "myPass1234";

    @Mock UserAdaptor userAdaptor;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserTokenServiceImpl userTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("token.secret-user", SECRET);
        userTokenService = new UserTokenServiceImpl(env, passwordEncoder, userAdaptor);

        user = User.builder()
                .username("user01")
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .name("홍길동")
                .role(Role.USER)
                .userUuid(UUID.randomUUID().toString())
                .build();
    }

    @Test
    @DisplayName("로그인 성공 - Bearer accessToken 발급, 클레임에 username·권한 포함")
    void login_success_issuesAccessToken() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        JwtToken token = userTokenService.login("user01", RAW_PASSWORD);

        assertThat(token.getGrantType()).isEqualTo("Bearer");
        assertThat(token.getAccessToken()).isNotBlank();

        Authentication authentication = userTokenService.getAuthentication(token.getAccessToken());
        assertThat(authentication.getName()).isEqualTo("user01");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(Role.USER.getKey());
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 시 PASSWORD_NOT_MATCH 예외")
    void login_wrongPassword_throws() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        assertThatThrownBy(() -> userTokenService.login("user01", "wrongPass123"))
                .isEqualTo(UserHandler.PASSWORD_NOT_MATCH);
    }
}
