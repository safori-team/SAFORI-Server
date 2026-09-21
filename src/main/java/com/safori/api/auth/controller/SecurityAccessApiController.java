package com.safori.api.auth.controller;

import com.safori.api.auth.dto.SignInRequest;
import com.safori.api.auth.dto.TokenReissueRequest;
import com.safori.api.auth.service.ReissueTokenUseCase;
import com.safori.api.auth.service.SignInUseCase;
import com.safori.api.auth.service.SignOutUseCase;
import com.safori.api.common.dto.ApiResponseDto;
import com.safori.security.dto.JwtToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[인증]", description = """
        로그인 · 토큰 재발급 · 로그아웃 API.

        로그인 후 발급된 accessToken을 이후 모든 요청의 `Authorization: Bearer {accessToken}` 헤더에 포함하세요.
        accessToken 만료 시 refreshToken으로 재발급하세요.
        """)
@Slf4j
@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
public class SecurityAccessApiController {

    private final SignInUseCase signInUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final SignOutUseCase signOutUseCase;

    @Operation(summary = "로그인",
            description = """
                    username + password로 인증하여 JWT 액세스 토큰을 발급합니다.
                    반환된 `accessToken`을 `Authorization: Bearer {accessToken}` 형태로 사용하세요.
                    """)
    @ApiResponse(responseCode = "200", description = "로그인 성공 — accessToken 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4052`: 존재하지 않는 유저입니다
            - `4053`: 비밀번호가 일치하지 않습니다
            """)
    @PostMapping("/sign-in")
    public ApiResponseDto<JwtToken> signIn(@Valid @RequestBody SignInRequest signInRequest) {
        return ApiResponseDto.onSuccess(signInUseCase.execute(signInRequest));
    }

    @Operation(summary = "토큰 재발급",
            description = """
                    refreshToken으로 새 accessToken / refreshToken을 발급합니다 (refreshToken 회전).
                    재발급 시 기존 refreshToken은 무효화됩니다.
                    """)
    @ApiResponse(responseCode = "200", description = "재발급 성공 — accessToken / refreshToken 반환")
    @ApiResponse(responseCode = "401", description = "- `4060`: 유효하지 않은 리프레시 토큰입니다")
    @PostMapping("/reissue")
    public ApiResponseDto<JwtToken> reissue(@Valid @RequestBody TokenReissueRequest tokenReissueRequest) {
        return ApiResponseDto.onSuccess(reissueTokenUseCase.execute(tokenReissueRequest));
    }

    @Operation(summary = "로그아웃",
            description = "refreshToken을 서버에서 무효화합니다. 이후 해당 refreshToken으로는 재발급이 불가합니다.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @DeleteMapping("/sign-out")
    public ApiResponseDto<String> signOut(@RequestParam String refreshToken) {
        return ApiResponseDto.onSuccess(signOutUseCase.execute(refreshToken));
    }
}
