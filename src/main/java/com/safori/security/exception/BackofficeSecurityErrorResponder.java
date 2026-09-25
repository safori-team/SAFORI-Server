package com.safori.security.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.common.dto.ApiResponseDto;
import com.safori.common.exception.ErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 백오피스 체인의 필터 단 거부 응답. 인증 실패는 401, 권한·범위 부족은 403을 API 공통 응답 형식으로 내려준다.
 */
@Component
@RequiredArgsConstructor
public class BackofficeSecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorStatus._UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorStatus._FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorStatus status) throws IOException {
        response.setStatus(status.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponseDto.onFailure(status.getCode(), status.getMessage(), null));
    }
}
