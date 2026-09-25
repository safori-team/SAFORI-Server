package com.safori.api.worker.dto;

import com.safori.common.consts.AccountStaticValues;
import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "담당자 등록 요청")
public record RegisterWorkerRequest(
        @Schema(description = "이름", example = "박담당")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "연락처 (하이픈 있어도 됨)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "직종. 사회복지사·생활복지사를 고르면 그 글자를, 직접입력이면 입력한 값을 보낸다", example = "사회복지사")
        @NotBlank @Size(max = 50)
        String jobTitle,

        @Schema(description = "계정 상태. true=활성화, false=비활성화(로그인 불가)", example = "true")
        @NotNull
        Boolean active,

        @Schema(description = "아이디 (6~12자, 영문·숫자). 중복 확인은 GET /v1/api/auth/check-login-id", example = "worker01")
        @NotBlank
        @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.LOGIN_ID_PATTERN, message = "아이디는 영문, 숫자만 사용할 수 있습니다")
        String loginId,

        @Schema(description = "비밀번호 (8~20자, 영문·숫자 혼합). 비밀번호 확인 일치는 화면에서 검사한다", example = "workPass1234")
        @NotBlank
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.PASSWORD_PATTERN, message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
        String password
) {
}
