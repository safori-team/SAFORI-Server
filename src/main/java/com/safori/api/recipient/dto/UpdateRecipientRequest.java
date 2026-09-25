package com.safori.api.recipient.dto;

import com.safori.common.consts.AccountStaticValues;
import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "대상자 정보 수정 요청. 화면의 모든 값을 보낸다(비밀번호만 바꿀 때 보냄).")
public record UpdateRecipientRequest(
        @Schema(description = "이름", example = "홍길동")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "연락처 (하이픈 있어도 됨). 없으면 null", example = "010-1234-5678")
        @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "생년월일. 없으면 null", example = "1960-03-12")
        LocalDate birthDate,

        @Schema(description = "이용 상태. true=활성화, false=비활성화(현황·목록·판정에서 빠짐, 앱 로그인은 그대로)", example = "true")
        @NotNull
        Boolean active,

        @Schema(description = "아이디 (6~12자, 영문·숫자). 바꾸면 중복 확인(GET /v1/api/auth/check-login-id) 후 보낸다", example = "elder01")
        @NotBlank
        @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.LOGIN_ID_PATTERN, message = "아이디는 영문, 숫자만 사용할 수 있습니다")
        String loginId,

        @Schema(description = "새 비밀번호 (8~20자, 영문·숫자 혼합). 바꾸지 않으면 null. 비밀번호 확인 일치는 화면에서 검사한다", example = "newPass1234")
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.PASSWORD_PATTERN, message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
        String password
) {
}
