package com.safori.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "토큰 재발급 요청")
@Builder
@Getter
@RequiredArgsConstructor
public class TokenReissueRequest {
    @Schema(description = "로그인 시 발급받은 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank
    private final String refreshToken;
}
