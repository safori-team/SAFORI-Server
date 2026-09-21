package com.safori.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "로그인 요청")
@Builder
@Getter
@RequiredArgsConstructor
public class SignInRequest {
    @Schema(description = "로그인 아이디 (6~12자 영문·숫자)", example = "user01")
    private final String username;
    @Schema(description = "비밀번호 (8~20자 영문·숫자 혼합)", example = "myPass1234")
    private final String password;
}
