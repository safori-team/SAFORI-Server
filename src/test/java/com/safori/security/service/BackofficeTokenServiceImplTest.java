package com.safori.security.service;

import com.safori.security.dto.AccountRole;
import com.safori.security.dto.BackofficeTokenClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeTokenServiceImplTest {

    private static final String BACKOFFICE_SECRET = "dGVzdC1vbmx5LWJhY2tvZmZpY2Utc2VjcmV0LWZvci1zYWZvcmktMjAyNi1qd3Q=";
    private static final String USER_SECRET = "ZHVtbXktc2VjcmV0LWZvci10ZXN0LW9ubHktc2Fmb3JpLTIwMjYtand0LWtleQ==";
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    private final BackofficeTokenServiceImpl service = serviceAt(NOW);

    @Test
    @DisplayName("발급한 토큰에서 계정·기관 컨텍스트·auth_version을 그대로 꺼낸다")
    void issuedTokenRoundTrips() {
        String token = service.issueAccessToken("account-uuid", "organization-public-id", 3L, AccountRole.ORG_ADMIN);

        assertThat(service.parse(token))
                .contains(new BackofficeTokenClaims("account-uuid", "organization-public-id", 3L, AccountRole.ORG_ADMIN));
    }

    @Test
    @DisplayName("만료된 토큰은 클레임을 꺼내지 않고 거부한다")
    void expiredTokenIsRejected() {
        String token = service.issueAccessToken("account-uuid", "organization-public-id", 0L, AccountRole.ORG_ADMIN);

        BackofficeTokenServiceImpl later = serviceAt(NOW.plus(BackofficeTokenServiceImpl.ACCESS_TOKEN_VALIDITY).plusSeconds(1));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("어르신 앱 토큰은 거부한다 — 다른 키로 서명됐고 백오피스 토큰 종류·audience가 없다")
    void appUserTokenIsRejected() {
        String appUserToken = Jwts.builder()
                .setSubject("user01")
                .claim("auth", "ROLE_USER")
                .setExpiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(USER_SECRET)), SignatureAlgorithm.HS256)
                .compact();

        assertThat(service.parse(appUserToken)).isEmpty();
    }

    @Test
    @DisplayName("같은 키로 서명됐어도 토큰 종류·audience가 다르면 거부한다")
    void tokenWithoutBackofficeKindIsRejected() {
        String foreignShaped = Jwts.builder()
                .setIssuer(BackofficeTokenServiceImpl.ISSUER)
                .setSubject("account-uuid")
                .claim(BackofficeTokenServiceImpl.ORGANIZATION_CLAIM, "organization-public-id")
                .claim(BackofficeTokenServiceImpl.AUTH_VERSION_CLAIM, 0L)
                .setExpiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(BACKOFFICE_SECRET)), SignatureAlgorithm.HS256)
                .compact();

        assertThat(service.parse(foreignShaped)).isEmpty();
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("시크릿이 없으면 비활성 — 검증은 모두 실패하고 발급은 예외")
    void disabledWithoutSecret() {
        BackofficeTokenServiceImpl disabled = new BackofficeTokenServiceImpl("", USER_SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = service.issueAccessToken("account-uuid", "organization-public-id", 0L, AccountRole.ORG_ADMIN);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.parse(token)).isEmpty();
        assertThatThrownBy(() -> disabled.issueAccessToken("account-uuid", "organization-public-id", 0L, AccountRole.ORG_ADMIN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("백오피스 시크릿이 어르신 앱 시크릿과 같으면 기동을 막는다")
    void sameSecretAsUserTokenIsRejected() {
        assertThatThrownBy(() -> new BackofficeTokenServiceImpl(USER_SECRET, USER_SECRET, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static BackofficeTokenServiceImpl serviceAt(Instant now) {
        return new BackofficeTokenServiceImpl(BACKOFFICE_SECRET, USER_SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }
}
