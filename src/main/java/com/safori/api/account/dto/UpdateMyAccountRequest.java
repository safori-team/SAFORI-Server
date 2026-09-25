package com.safori.api.account.dto;

import com.safori.common.consts.AccountStaticValues;
import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = """
        내 정보 수정 요청. 바꾸지 않는 항목은 null로 보낸다.
        관리자는 이름·연락처·아이디·비밀번호를, 담당자·보호자는 비밀번호만 바꿀 수 있다.""")
public record UpdateMyAccountRequest(
        @Schema(description = "이름 (관리자만)", example = "이관리")
        @Size(min = 1, max = 50)
        String name,

        @Schema(description = "연락처 (관리자만, 하이픈 있어도 됨)", example = "010-1234-5678")
        @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "아이디 (관리자만, 6~12자, 영문·숫자). 바꾸면 중복 확인(GET /v1/api/auth/check-login-id) 후 보낸다. 지금 아이디와 같으면 그대로 둔다", example = "orgadmin01")
        @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.LOGIN_ID_PATTERN, message = "아이디는 영문, 숫자만 사용할 수 있습니다")
        String loginId,

        @Schema(description = "새 비밀번호 (8~20자, 영문·숫자 혼합). 비밀번호 확인 일치는 화면에서 검사한다", example = "newPass1234")
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.PASSWORD_PATTERN, message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
        String password
) {
}
