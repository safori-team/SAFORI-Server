package com.safori.api.operator.dto;

import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "기관 관리자 교체 요청. 새 관리자 계정을 만든다(검증은 기관 생성과 같다).")
public record ReplaceOrganizationAdminRequest(
        @Schema(description = "새 관리자 이름", example = "홍길동")
        @NotBlank @Size(max = 50)
        String adminName,

        @Schema(description = "새 관리자 휴대폰 번호 (하이픈 있어도 됨)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String adminPhone,

        @Schema(description = "새 관리자 로그인 아이디", example = "safori_admin2")
        @NotBlank @Size(max = 64)
        String adminLoginId,

        @Schema(description = "새 관리자 임시 비밀번호. 관리자에게 따로 전달한다.", example = "tempPass1234")
        @NotBlank @Size(min = 8, max = 64)
        String adminPassword
) {
}
