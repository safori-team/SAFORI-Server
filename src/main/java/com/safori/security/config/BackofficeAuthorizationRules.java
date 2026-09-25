package com.safori.security.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 백오피스·보호자 경로의 URL 단위(필터 단) 인가 규칙.
 *
 * <p>API 담당자는 엔드포인트를 추가할 때 이 타입의 빈을 등록해 경로별 필요 권한을 선언한다. 어떤 규칙에도
 * 걸리지 않는 백오피스 경로는 {@code denyAll}이라, 규칙을 빠뜨린 API는 열리지 않고 403으로 닫힌다.
 * 규칙 빈 안에서 {@code anyRequest()}를 호출하지 않는다(기본 거부 규칙과 충돌한다).
 *
 * <pre>{@code
 * @Bean
 * BackofficeAuthorizationRules recipientApiRules(BackofficeRequestAuthorization authz) {
 *     return registry -> registry
 *             .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/backoffice/recipients/{recipientId}"))
 *             .access(authz.recipient(PermissionCode.RECIPIENT_READ, "recipientId"))
 *             .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/backoffice/recipients"))
 *             .access(authz.permission(PermissionCode.RECIPIENT_CREATE));
 * }
 * }</pre>
 *
 * <p>URL 규칙은 1차 경계다. UseCase에도 {@code @PreAuthorize}로 같은 판정을 걸고, 목록 조회는
 * {@code BackofficeAccessPolicy#recipientScope}로 쿼리 범위를 제한한다.
 */
@FunctionalInterface
public interface BackofficeAuthorizationRules {

    void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry);
}
