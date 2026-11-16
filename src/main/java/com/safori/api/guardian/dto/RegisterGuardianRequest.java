package com.safori.api.guardian.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safori.common.consts.AccountStaticValues;
import com.safori.common.util.PhoneNumber;
import com.safori.domain.care.entity.GuardianRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

@Schema(description = "보호자 등록 요청. 대상자를 고르면 등록과 함께 연결하고, 비우면 나중에 대상자 상세·보호자 상세에서 연결한다.")
public record RegisterGuardianRequest(
        @Schema(description = "이름", example = "김희영")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "연락처 (하이픈 있어도 됨)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "연결할 대상자 식별자 (public_id). 비우면 연결하지 않는다")
        String careRecipientId,

        @Schema(description = "대상자와의 관계. 대상자를 고르면 필수", example = "CHILD")
        GuardianRelation relation,

        @Schema(description = "관계가 OTHER(기타)일 때 직접 입력한 값", example = "며느리")
        @Size(max = 50)
        String relationText,

        @Schema(description = "계정 상태. true=활성화, false=비활성화(로그인 불가)", example = "true")
        @NotNull
        Boolean active,

        @Schema(description = "아이디 (6~12자, 영문·숫자). 중복 확인은 GET /v1/api/auth/check-login-id", example = "guard01")
        @NotBlank
        @Size(min = 6, max = 12, message = "아이디는 6~12자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.LOGIN_ID_PATTERN, message = "아이디는 영문, 숫자만 사용할 수 있습니다")
        String loginId,

        @Schema(description = "비밀번호 (8~20자, 영문·숫자 혼합)", example = "guardPass1234")
        @NotBlank
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자로 입력해 주세요")
        @Pattern(regexp = AccountStaticValues.PASSWORD_PATTERN, message = "비밀번호는 영문, 숫자를 혼합하여 입력해 주세요")
        String password
) {

    @JsonIgnore
    @AssertTrue(message = "대상자를 고르면 관계를 함께 보내야 합니다 (기타는 직접 입력값 필수)")
    public boolean isRelationValid() {
        if (!StringUtils.hasText(careRecipientId)) {
            return true;
        }
        return relation != null && (relation != GuardianRelation.OTHER || StringUtils.hasText(relationText));
    }
}
