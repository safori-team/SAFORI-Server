package com.safori.api.guardian.dto;

import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "보호자 정보 수정 요청. 아이디·비밀번호는 바꾸지 않고, 관계는 연결 API로 바꾼다.")
public record UpdateGuardianRequest(
        @Schema(description = "이름", example = "김희영")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "연락처 (하이픈 있어도 됨)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "계정 상태. true=활성화, false=비활성화(로그인 불가, 로그인 중이면 다음 요청부터 끊김)", example = "true")
        @NotNull
        Boolean active
) {
}
