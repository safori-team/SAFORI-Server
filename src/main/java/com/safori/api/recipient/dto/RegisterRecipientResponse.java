package com.safori.api.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록된 대상자")
public record RegisterRecipientResponse(
        @Schema(description = "대상자 외부 식별자 (care_recipient.public_id)") String recipientPublicId
) {
}
