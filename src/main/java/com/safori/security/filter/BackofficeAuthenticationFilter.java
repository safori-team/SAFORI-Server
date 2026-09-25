package com.safori.security.filter;

import com.safori.domain.access.policy.AuthenticatedActor;
import com.safori.domain.access.policy.BackofficeActorResolver;
import com.safori.security.dto.AccountRole;
import com.safori.security.service.BackofficeTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 백오피스 체인 전용 인증 필터. 토큰 검증 후 요청 시점의 DB 상태로 주체와 권한을 다시 만든다.
 *
 * <p>토큰이 없거나 무효이거나, 계정·기관·멤버십이 활성이 아니거나, 토큰 발급 후 {@code auth_version}이
 * 바뀌었으면 인증하지 않는다 — 뒤따르는 인가 단계에서 401이 된다.
 *
 * <p>빈으로 등록하지 않는다. {@code @Component}로 등록하면 Spring Boot가 모든 요청의 서블릿 필터로도 걸어
 * 어르신 앱 경로까지 실행되기 때문이다. {@code BackofficeSecurityConfig}가 백오피스 체인에만 넣는다.
 */
@RequiredArgsConstructor
public class BackofficeAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";

    private final BackofficeTokenService backofficeTokenService;
    private final BackofficeActorResolver backofficeActorResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            backofficeTokenService.parse(token)
                    .flatMap(claims -> backofficeActorResolver.resolveActive(
                                    claims.accountUuid(), claims.organizationPublicId(), claims.authVersion())
                            .map(authenticated -> toAuthentication(authenticated, claims.role())))
                    .ifPresent(authentication -> {
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);
                    });
        }
        filterChain.doFilter(request, response);
    }

    /**
     * principal은 {@code BackofficeActor}, authority는 요청 시점에 계산한 권한 코드({@code PermissionCode} 이름)다.
     * 그래서 URL 규칙에서 {@code hasAuthority("RECIPIENT_READ")}로 검사할 수 있다. 데이터 범위(배정·연결)는
     * authority에 담지 않고 {@code BackofficeAccessPolicy}가 따로 판정한다.
     */
    public static Authentication toAuthentication(AuthenticatedActor authenticated) {
        return toAuthentication(authenticated, null);
    }

    /** 토큰의 역할은 {@code ROLE_} authority로 붙인다. 내 정보 조회에서 역할을 DB 조회 없이 꺼내기 위해서다. */
    public static Authentication toAuthentication(AuthenticatedActor authenticated, AccountRole role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>(authenticated.permissions().permissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList());
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
        }
        return new UsernamePasswordAuthenticationToken(authenticated.actor(), "", authorities);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
