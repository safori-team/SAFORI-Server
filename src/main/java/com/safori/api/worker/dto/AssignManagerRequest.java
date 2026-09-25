package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "대상자에게 배정할 담당자")
public record AssignManagerRequest(
        @Schema(description = "담당자 식별자 (계정 UUID)") @NotBlank String managerId
) {
}
