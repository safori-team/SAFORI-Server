package com.safori.api.auth.controller;

import com.safori.api.auth.dto.CheckLoginIdResponse;
import com.safori.api.auth.dto.SignInRequest;
import com.safori.api.auth.dto.TokenReissueRequest;
import com.safori.api.auth.service.CheckLoginIdUseCase;
import com.safori.api.auth.service.ReissueTokenUseCase;
import com.safori.api.auth.service.SignInUseCase;
import com.safori.api.auth.service.SignOutUseCase;
import com.safori.api.common.dto.ApiResponseDto;
import com.safori.security.dto.JwtToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[인증]")),
     description = """
        로그인 · 토큰 재발급 · 로그아웃 API.

        로그인 후 발급된 accessToken을 이후 모든 요청의 `Authorization: Bearer {accessToken}` 헤더에 포함하세요.
        accessToken 만료 시 refreshToken으로 재발급하세요.
        """)
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
public class SecurityAccessApiController {

    private final SignInUseCase signInUseCase;
    private final CheckLoginIdUseCase checkLoginIdUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final SignOutUseCase signOutUseCase;

    @Operation(operationId = "signIn", summary = "로그인",
            description = """
                    username + password로 인증하여 JWT 액세스 토큰을 발급합니다.
                    어르신·기관 관리자·담당자·보호자 모두 이 엔드포인트로 로그인합니다(아이디는 전체에서 유일).
                    응답의 `role`(ELDER / ORG_ADMIN / CARE_WORKER / GUARDIAN)로 화면을 고르세요.
                    반환된 `accessToken`을 `Authorization: Bearer {accessToken}` 형태로 사용하세요.
                    """)
    @ApiResponse(responseCode = "200", description = "로그인 성공 — accessToken 반환")
    @ApiResponse(responseCode = "400", description = """
            - `4052`: 존재하지 않는 유저입니다
            - `4053`: 비밀번호가 일치하지 않습니다
            - `4351`: 사용할 수 없는 백오피스 계정입니다 (정지, 승인 대기, 소속 종료, 비활성 기관)
            """)
    @PostMapping("/sign-in")
    public ApiResponseDto<JwtToken> signIn(@Valid @RequestBody SignInRequest signInRequest) {
        return ApiResponseDto.onSuccess(signInUseCase.execute(signInRequest));
    }

    @Operation(operationId = "reissue", summary = "토큰 재발급",
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

    @Operation(operationId = "signOut", summary = "로그아웃",
            description = "refreshToken을 서버에서 무효화합니다. 이후 해당 refreshToken으로는 재발급이 불가합니다.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @DeleteMapping("/sign-out")
    public ApiResponseDto<String> signOut(@RequestParam String refreshToken) {
        return ApiResponseDto.onSuccess(signOutUseCase.execute(refreshToken));
    }

    @Operation(operationId = "checkLoginId", summary = "아이디 중복 확인",
            description = """
                    아이디를 사용할 수 있는지 확인합니다. 로그인 없이 호출합니다.
                    어르신 회원가입과 담당자·보호자 등록 폼이 같이 사용합니다(아이디는 모든 계정을 통틀어 유일).
                    확인 후 가입·등록 전에 다른 사람이 같은 아이디를 쓸 수 있으므로, 가입·등록 API도 중복이면 실패합니다.
                    """)
    @ApiResponse(responseCode = "200", description = "확인 성공 — available=true면 사용 가능")
    @GetMapping("/check-login-id")
    public ApiResponseDto<CheckLoginIdResponse> checkLoginId(
            @Parameter(description = "확인할 아이디", example = "worker01") @RequestParam @NotBlank String loginId) {
        return ApiResponseDto.onSuccess(checkLoginIdUseCase.execute(loginId));
    }
}
