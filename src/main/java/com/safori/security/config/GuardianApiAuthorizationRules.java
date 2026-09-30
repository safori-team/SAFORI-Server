package com.safori.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 보호자 화면 API({@code /v1/api/guardian/**}) 권한 규칙. 모두 GUARDIAN_STATUS_READ 하나로 연다.
 * 범위(연결된 대상자·공개 일지)는 목록은 서비스가, 상세는 대상자 범위 규칙과 서비스가 거른다.
 */
@Configuration
public class GuardianApiAuthorizationRules {

    @Bean
    BackofficeAuthorizationRules guardianApiAuthorization(BackofficeRequestAuthorization authz) {
        return registry -> registry
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/guardian/care-recipients"),
                        antMatcher(HttpMethod.GET, "/v1/api/guardian/journals"))
                .access(authz.permission(GUARDIAN_STATUS_READ))
                .requestMatchers(antMatcher(HttpMethod.GET,
                        "/v1/api/guardian/care-recipients/{careRecipientId}/journals/{journalId}"))
                .access(authz.recipient(GUARDIAN_STATUS_READ, "careRecipientId"));
    }
}
