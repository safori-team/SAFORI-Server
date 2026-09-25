package com.safori.security.config;

import com.safori.domain.access.policy.BackofficeActorResolver;
import com.safori.security.filter.BackofficeAuthenticationFilter;
import com.safori.security.filter.JwtAuthenticationFilter;
import com.safori.security.service.BackofficeTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    public static final String USER_INFO_PATH = "/v1/api/users";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/.well-known/**", "/*.ico", "/error", "/images/**").permitAll()
                        .requestMatchers(permitAllRequests()).permitAll()
                        .requestMatchers(swaggerRequests()).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 내 정보 조회({@code GET /v1/api/users})만 어르신 앱 토큰과 백오피스 토큰을 모두 받는다.
     * 두 토큰은 서명 키가 달라 하나만 인증된다. 다른 어르신 API에는 백오피스 토큰이 통하지 않도록 이 경로 하나로 좁힌다.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain userInfoFilterChain(HttpSecurity http,
                                                   BackofficeTokenService backofficeTokenService,
                                                   BackofficeActorResolver backofficeActorResolver) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher(USER_INFO_PATH, HttpMethod.GET.name()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new BackofficeAuthenticationFilter(backofficeTokenService, backofficeActorResolver),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JwtAuthenticationFilter는 @Component라 Spring Boot가 모든 요청의 서블릿 필터로도 자동 등록한다.
     * 이 체인 안에서만 돌도록 서블릿 등록은 끈다 — 백오피스 체인 경로에서 어르신 앱 토큰을 파싱하지 않게 한다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private RequestMatcher[] permitAllRequests() {
        return List.of(
                new AntPathRequestMatcher("/actuator/**"),
                new AntPathRequestMatcher("/v1/api/auth/**"),
                new AntPathRequestMatcher("/v1/api/users/sign-up")
        ).toArray(RequestMatcher[]::new);
    }

    private RequestMatcher[] swaggerRequests() {
        return List.of(
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/swagger-ui.html"),
                new AntPathRequestMatcher("/swagger-resources/**"),
                new AntPathRequestMatcher("/v3/api-docs/**")
        ).toArray(RequestMatcher[]::new);
    }
}
