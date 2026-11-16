package com.safori.api.recipient.dto;

import com.safori.domain.care.entity.CareProcessingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "처리 상태 변경")
public record ChangeProcessingStatusRequest(
        @Schema(description = "UNCHECKED(미확인) / IN_PROGRESS(진행 중) / DONE(완료 — 상태 코드가 X가 된다)", example = "IN_PROGRESS")
        @NotNull CareProcessingStatus processingStatus
) {
}
