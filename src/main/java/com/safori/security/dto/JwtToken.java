package com.safori.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "JWT 토큰 응답")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JwtToken {
    @Schema(description = "토큰 타입", example = "Bearer")
    private String grantType;
    @Schema(description = "API 요청에 사용할 Access Token (Authorization 헤더에 포함)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;
    @Schema(description = "Access Token 갱신에 사용할 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;
    @Schema(description = "로그인한 주체의 역할. 클라이언트가 화면을 고르는 데 쓴다.", example = "ELDER")
    private AccountRole role;
}
