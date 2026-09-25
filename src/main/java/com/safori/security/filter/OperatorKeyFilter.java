package com.safori.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SAFORI 운영자 API 인증. {@value #HEADER} 헤더를 설정된 운영자 키와 상수 시간으로 비교한다.
 *
 * <p>키가 설정되지 않았으면 어떤 요청도 인증하지 않는다(빈 헤더 == 빈 키로 통과하는 일을 막는다).
 * 헤더 값은 로그에 남기지 않는다. {@code @Component}가 아니라 운영자 체인 안에서만 동작한다.
 */
public class OperatorKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Operator-Key";

    private final byte[] operatorKey;

    public OperatorKeyFilter(String operatorKey) {
        this.operatorKey = operatorKey == null || operatorKey.isBlank()
                ? null
                : operatorKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (operatorKey != null && provided != null
                && MessageDigest.isEqual(operatorKey, provided.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "operator", null, AuthorityUtils.createAuthorityList("ROLE_OPERATOR")));
        }
        chain.doFilter(request, response);
    }
}
