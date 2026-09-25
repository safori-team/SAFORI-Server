package com.safori.api.worker.dto;

import com.safori.common.util.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "담당자 정보 수정 요청. 등록 폼의 기본 정보와 같은 항목이며, 아이디·비밀번호는 바꾸지 않는다.")
public record UpdateWorkerRequest(
        @Schema(description = "이름", example = "박담당")
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = "연락처 (하이픈 있어도 됨)", example = "010-1234-5678")
        @NotBlank @Pattern(regexp = PhoneNumber.PATTERN, message = "휴대폰 번호 형식이 올바르지 않습니다")
        String phone,

        @Schema(description = "직종. 사회복지사·생활복지사를 고르면 그 글자를, 직접입력이면 입력한 값을 보낸다", example = "생활복지사")
        @NotBlank @Size(max = 50)
        String jobTitle,

        @Schema(description = "계정 상태. true=활성화, false=비활성화(로그인 불가, 로그인 중이면 다음 요청부터 끊김)", example = "true")
        @NotNull
        Boolean active
) {
}
