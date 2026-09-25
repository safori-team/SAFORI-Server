package com.safori.api.operator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "기관 + 최초 기관 관리자 생성 요청")
@Builder
@Getter
@RequiredArgsConstructor
public class CreateOrganizationRequest {
    @Schema(description = "기관 이름", example = "사포리 복지관")
    @NotBlank
    @Size(max = 100)
    private final String organizationName;

    @Schema(description = "관리자 로그인 아이디", example = "safori_admin")
    @NotBlank
    @Size(max = 64)
    private final String adminLoginId;

    @Schema(description = "관리자 임시 비밀번호. 관리자에게 따로 전달한다.", example = "tempPass1234")
    @NotBlank
    @Size(min = 8, max = 64)
    private final String adminPassword;

    @Schema(description = "관리자 이름", example = "홍길동")
    @NotBlank
    @Size(max = 50)
    private final String adminName;
}
