package com.safori.security.service;

import com.safori.security.dto.AccountRole;
import com.safori.security.dto.BackofficeTokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BackofficeTokenServiceImpl implements BackofficeTokenService {

    static final String ISSUER = "safori";
    static final String AUDIENCE = "safori-backoffice";
    static final String TOKEN_KIND_CLAIM = "kind";
    static final String TOKEN_KIND = "backoffice-access";
    static final String ORGANIZATION_CLAIM = "org";
    static final String AUTH_VERSION_CLAIM = "ver";
    static final String ROLE_CLAIM = "role";
    static final Duration ACCESS_TOKEN_VALIDITY = Duration.ofMinutes(30);

    /** 시크릿 미설정이면 null — 발급·검증 모두 비활성. */
    private final Key key;
    private final Clock clock;

    @Autowired
    public BackofficeTokenServiceImpl(@Value("${token.secret-backoffice:}") String backofficeSecret,
                                      @Value("${token.secret-user:}") String userSecret) {
        this(backofficeSecret, userSecret, Clock.systemUTC());
    }

    BackofficeTokenServiceImpl(String backofficeSecret, String userSecret, Clock clock) {
        this.clock = clock;
        if (!StringUtils.hasText(backofficeSecret)) {
            log.info("token.secret-backoffice 미설정 — 백오피스 인증 비활성화 (백오피스·보호자 경로는 모두 401)");
            this.key = null;
            return;
        }
        if (backofficeSecret.equals(userSecret)) {
            throw new IllegalStateException("token.secret-backoffice는 token.secret-user와 다른 값이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(backofficeSecret));
    }

    @Override
    public String issueAccessToken(String accountUuid, String organizationPublicId, long authVersion,
                                   AccountRole role) {
        if (key == null) {
            throw new IllegalStateException("token.secret-backoffice가 설정되지 않아 백오피스 토큰을 발급할 수 없습니다.");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setSubject(accountUuid)
                .claim(TOKEN_KIND_CLAIM, TOKEN_KIND)
                .claim(ORGANIZATION_CLAIM, organizationPublicId)
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .claim(ROLE_CLAIM, role.name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ACCESS_TOKEN_VALIDITY)))
                .setId(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public Optional<BackofficeTokenClaims> parse(String token) {
        if (key == null || !StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            // 만료 토큰은 ExpiredJwtException으로 거부된다. 만료 클레임을 꺼내 쓰지 않는다.
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setClock(() -> Date.from(clock.instant()))
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .require(TOKEN_KIND_CLAIM, TOKEN_KIND)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String organizationPublicId = claims.get(ORGANIZATION_CLAIM, String.class);
            Long authVersion = claims.get(AUTH_VERSION_CLAIM, Long.class);
            String role = claims.get(ROLE_CLAIM, String.class);
            if (!StringUtils.hasText(claims.getSubject()) || !StringUtils.hasText(organizationPublicId)
                    || authVersion == null || !StringUtils.hasText(role)) {
                return Optional.empty();
            }
            return Optional.of(new BackofficeTokenClaims(claims.getSubject(), organizationPublicId, authVersion,
                    AccountRole.valueOf(role)));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("백오피스 토큰 거부: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isEnabled() {
        return key != null;
    }
}
