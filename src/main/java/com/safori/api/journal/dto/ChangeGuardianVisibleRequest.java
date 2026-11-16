package com.safori.api.journal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "일지 보호자 공개 여부 변경 요청")
public record ChangeGuardianVisibleRequest(
        @Schema(description = "보호자 공개 여부", example = "true")
        @NotNull
        Boolean guardianVisible
) {
}
