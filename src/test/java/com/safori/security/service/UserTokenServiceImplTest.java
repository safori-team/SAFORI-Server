package com.safori.security.service;

import com.safori.common.service.RefreshTokenService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.exception.UserHandler;
import com.safori.security.dto.JwtToken;
import com.safori.security.exception.AuthHandler;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceImplTest {

    private static final String SECRET =
            "ZHVtbXktc2VjcmV0LWZvci10ZXN0LW9ubHktc2Fmb3JpLTIwMjYtand0LWtleQ==";
    private static final String RAW_PASSWORD = "myPass1234";

    @Mock UserAdaptor userAdaptor;
    @Mock RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserTokenServiceImpl userTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("token.secret-user", SECRET);
        userTokenService = new UserTokenServiceImpl(env, passwordEncoder, userAdaptor, refreshTokenService);

        user = User.builder()
                .username("user01")
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .name("홍길동")
                .role(Role.USER)
                .userUuid(UUID.randomUUID().toString())
                .build();
    }

    @Test
    @DisplayName("로그인 성공 - access/refresh 발급, refresh를 화이트리스트에 저장")
    void login_success_issuesTokensAndStoresRefresh() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        JwtToken token = userTokenService.login("user01", RAW_PASSWORD);

        assertThat(token.getGrantType()).isEqualTo("Bearer");
        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
        verify(refreshTokenService).setValue(token.getRefreshToken(), "user01");

        Authentication authentication = userTokenService.getAuthentication(token.getAccessToken());
        assertThat(authentication.getName()).isEqualTo("user01");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(Role.USER.getKey());
    }

    @Test
    @DisplayName("만료된 access token은 인증되지 않는다 (서명이 맞아도 거부)")
    void getAuthentication_expiredToken_throws() {
        String expired = Jwts.builder()
                .setSubject("user01")
                .claim("auth", Role.USER.getKey())
                .setExpiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> userTokenService.getAuthentication(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 시 PASSWORD_NOT_MATCH 예외")
    void login_wrongPassword_throws() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);

        assertThatThrownBy(() -> userTokenService.login("user01", "wrongPass123"))
                .isEqualTo(UserHandler.PASSWORD_NOT_MATCH);
    }

    @Test
    @DisplayName("토큰 재발급 성공 - 기존 refresh 삭제(회전) 후 새 토큰 발급")
    void reissue_success_rotatesRefreshToken() {
        given(userAdaptor.queryUserByUsername("user01")).willReturn(user);
        JwtToken issued = userTokenService.login("user01", RAW_PASSWORD);
        String oldRefresh = issued.getRefreshToken();
        given(refreshTokenService.getValue(oldRefresh)).willReturn("user01");

        JwtToken reissued = userTokenService.reissueToken(oldRefresh);

        verify(refreshTokenService).deleteValue(oldRefresh);
        assertThat(reissued.getAccessToken()).isNotBlank();
        assertThat(reissued.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 화이트리스트에 없는 refresh면 INVALID_REFRESH_TOKEN 예외")
    void reissue_unknownRefresh_throws() {
        given(refreshTokenService.getValue("ghost")).willReturn(null);

        assertThatThrownBy(() -> userTokenService.reissueToken("ghost"))
                .isEqualTo(AuthHandler.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그아웃 - refresh를 화이트리스트에서 삭제하고 true 반환")
    void logout_deletesRefreshToken() {
        boolean result = userTokenService.logout("some-refresh");

        assertThat(result).isTrue();
        verify(refreshTokenService).deleteValue("some-refresh");
    }
}
