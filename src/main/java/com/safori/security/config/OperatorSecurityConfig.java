package com.safori.security.config;

import com.safori.security.exception.BackofficeSecurityErrorResponder;
import com.safori.security.filter.OperatorKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SAFORI 운영자 API 전용 보안 체인. 기관·최초 관리자 생성처럼 기관 안에 아직 권한자가 없는 작업을 맡는다.
 *
 * <p>백오피스·어르신 앱 토큰은 보지 않고 {@link OperatorKeyFilter}의 운영자 키만 확인한다.
 * {@code operator.api-key}가 비어 있으면 이 경로는 모두 401이다.
 */
@Configuration
@RequiredArgsConstructor
public class OperatorSecurityConfig {

    public static final String OPERATOR_API_PATTERN = "/v1/api/operator/**";

    private final BackofficeSecurityErrorResponder errorResponder;

    @Bean
    @Order(0)
    public SecurityFilterChain operatorFilterChain(HttpSecurity http,
                                                   @Value("${operator.api-key:}") String operatorKey)
            throws Exception {
        http
                .securityMatcher(OPERATOR_API_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .authorizeHttpRequests(registry -> registry.anyRequest().hasRole("OPERATOR"))
                .addFilterBefore(new OperatorKeyFilter(operatorKey), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
