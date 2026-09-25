package com.safori.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 권한(인가) 규칙이 아직 붙지 않은 백오피스 API. 로그인한 백오피스 구성원이면 권한과 관계없이 통과한다.
 *
 * <p>API를 먼저 만들고 권한은 나중에 붙이기로 분업했다. 권한 담당자는 여기서 경로를 하나씩 빼서
 * 실제 권한 규칙({@link BackofficeRequestAuthorization#permission}, {@code recipient})으로 옮긴다.
 * 로그인은 요구한다 — 요청한 구성원의 기관을 토큰에서 알아야 하기 때문이다.
 */
@Configuration
public class PendingAuthorizationRules {

    @Bean
    BackofficeAuthorizationRules pendingAuthorization(BackofficeRequestAuthorization authz) {
        return registry -> registry
                // 대상자 조회·등록 (권한 담당자: RECIPIENT_CREATE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/backoffice/recipients/lookup"),
                        antMatcher(HttpMethod.POST, "/v1/api/backoffice/recipients"))
                .access(authz.member())
                // 담당자 등록 (권한 담당자: MEMBER_MANAGE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/backoffice/workers"))
                .access(authz.member());
    }
}
