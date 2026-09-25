package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "담당자의 배정 대상자를 이 목록과 똑같이 맞춘다")
public record ReplaceManagerRecipientsRequest(
        @Schema(description = "체크된 대상자 식별자(public_id) 전체. 빈 목록이면 모두 배정 해제")
        @NotNull List<String> careRecipientIds
) {
}
