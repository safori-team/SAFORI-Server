package com.safori.security.config;

import com.safori.domain.access.policy.BackofficeActorResolver;
import com.safori.security.exception.BackofficeSecurityErrorResponder;
import com.safori.security.filter.BackofficeAuthenticationFilter;
import com.safori.security.service.BackofficeTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * 백오피스(기관 관리자·담당자)와 보호자 경로 전용 보안 체인.
 *
 * <p>요청 흐름: 백오피스 토큰 인증(요청마다 DB 상태 재확인) → URL 규칙({@link BackofficeAuthorizationRules})
 * → UseCase의 {@code @PreAuthorize} → 범위가 걸린 조회. 이 체인은 어르신 앱 체인({@link SecurityConfig})보다
 * 먼저 매칭되고, 경로가 겹치지 않아 기존 API에는 영향이 없다.
 *
 * <p>{@code @EnableMethodSecurity}는 애플리케이션 전역 설정이다. 현재 {@code @PreAuthorize}를 쓰는 곳은
 * 백오피스 UseCase뿐이다.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class BackofficeSecurityConfig {

    public static final String BACKOFFICE_API_PATTERN = "/v1/api/backoffice/**";
    public static final String GUARDIAN_API_PATTERN = "/v1/api/guardian/**";

    private final BackofficeTokenService backofficeTokenService;
    private final BackofficeActorResolver backofficeActorResolver;
    private final BackofficeSecurityErrorResponder backofficeSecurityErrorResponder;

    @Bean
    @Order(1)
    public SecurityFilterChain backofficeFilterChain(HttpSecurity http,
                                                     ObjectProvider<BackofficeAuthorizationRules> authorizationRules)
            throws Exception {
        http
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher(BACKOFFICE_API_PATTERN),
                        new AntPathRequestMatcher(GUARDIAN_API_PATTERN)))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(backofficeSecurityErrorResponder)
                        .accessDeniedHandler(backofficeSecurityErrorResponder))
                .authorizeHttpRequests(registry -> {
                    authorizationRules.orderedStream().forEach(rules -> rules.configure(registry));
                    // 규칙이 없는 경로는 닫는다. 토큰이 없으면 401, 있으면 403.
                    registry.anyRequest().denyAll();
                })
                .addFilterBefore(new BackofficeAuthenticationFilter(backofficeTokenService, backofficeActorResolver),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
