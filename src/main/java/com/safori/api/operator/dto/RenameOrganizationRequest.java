package com.safori.api.operator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "기관 이름 변경 요청")
public record RenameOrganizationRequest(
        @Schema(description = "새 기관 이름", example = "사포리 복지관")
        @NotBlank @Size(max = 100)
        String name
) {
}
