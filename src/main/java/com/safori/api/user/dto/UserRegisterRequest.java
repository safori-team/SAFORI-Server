package com.safori.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "회원가입 요청")
@Builder
@AllArgsConstructor
@Getter
public class UserRegisterRequest {

    @Schema(description = "사용자 이름 (실명)", example = "홍길동")
    @NotBlank
    private final String name;

    @Schema(description = "로그인 아이디 (6~12자, 영문·숫자만 허용)", example = "user01")
    @NotBlank
    @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "아이디는 영문, 숫자만 사용할 수 있습니다")
    private final String username;

    @Schema(description = "비밀번호 (8~20자, 영문·숫자 혼합 필수)", example = "myPass1234")
    @NotBlank
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
    private final String password;
}
