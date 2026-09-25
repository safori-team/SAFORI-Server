package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대상자의 현재 담당자")
public record AssignmentResponse(
        @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
        @Schema(description = "담당자 식별자 (계정 UUID). 배정 해제 후면 null") String managerId
) {
}
