package com.safori.api.user.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.api.user.service.SignUpUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[유저]", description = "회원가입 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users")
public class UserApiController {

    private final SignUpUseCase signUpUseCase;

    @Operation(summary = "회원가입",
            description = "username · password · name으로 새 계정을 생성합니다. 생성된 userId를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "회원가입 성공 — 생성된 userId 반환")
    @ApiResponse(responseCode = "400", description = "- `4050`: 이미 존재하는 username입니다")
    @PostMapping("/sign-up")
    public ApiResponseDto<Long> signUp(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        return ApiResponseDto.onSuccess(signUpUseCase.execute(userRegisterRequest));
    }
}
