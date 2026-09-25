package com.safori.api.user.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.user.dto.UserInfoResponse;
import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.api.user.service.GetUserInfoUseCase;
import com.safori.api.user.service.SignUpUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[유저]", description = "회원가입 및 내 정보 조회 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users")
public class UserApiController {

    private final SignUpUseCase signUpUseCase;
    private final GetUserInfoUseCase getUserInfoUseCase;

    @Operation(summary = "회원가입",
            description = "username · password · name으로 새 계정을 생성합니다. 생성된 userId를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "회원가입 성공 — 생성된 userId 반환")
    @ApiResponse(responseCode = "400", description = "- `4050`: 이미 존재하는 username입니다")
    @PostMapping("/sign-up")
    public ApiResponseDto<Long> signUp(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        return ApiResponseDto.onSuccess(signUpUseCase.execute(userRegisterRequest));
    }

    @Operation(summary = "내 정보 조회",
            description = """
                    현재 로그인한 주체의 정보를 반환합니다. 어르신 앱 토큰과 백오피스(관리자·담당자·보호자) 토큰 모두 받습니다.
                    `role`로 화면 종류를, `permissions`로 메뉴 노출 여부를 판단하세요. 어르신은 `permissions`가 빈 목록, `organization`이 null입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @GetMapping
    public ApiResponseDto<UserInfoResponse> getUserInfo(@Parameter(hidden = true) Authentication authentication) {
        return ApiResponseDto.onSuccess(getUserInfoUseCase.execute(authentication));
    }
}
