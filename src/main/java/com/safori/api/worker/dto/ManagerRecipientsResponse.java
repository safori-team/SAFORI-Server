package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "변경 후 담당자의 배정 대상자")
public record ManagerRecipientsResponse(
        @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
        @Schema(description = "현재 배정 대상자 식별자(public_id)") List<String> careRecipientIds
) {
}
