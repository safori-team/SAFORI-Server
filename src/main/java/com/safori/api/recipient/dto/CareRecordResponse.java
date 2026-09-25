package com.safori.api.recipient.dto;

import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.organization.entity.OrganizationMember;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "대상자 기록")
public record CareRecordResponse(
        @Schema(description = "기록 식별자") String recordId,
        @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
        @Schema(description = "상태 코드") CareStatusCode statusCode,
        @Schema(description = "사유 종류") String reasonType,
        @Schema(description = "사유 문구") String reasonMessage,
        @Schema(description = "요청·감지 시각") LocalDateTime detectedAt,
        @Schema(description = "처리 상태 (UNCHECKED / IN_PROGRESS / DONE / ABSORBED=더 높은 기록에 흡수됨)")
        CareProcessingStatus processingStatus,
        @Schema(description = "현재 기록이면 true. 현재 기록만 처리 상태를 바꿀 수 있다") boolean current,
        @Schema(description = "마지막으로 처리 상태를 바꾼 구성원 이름") String processedByName,
        @Schema(description = "처리 상태를 바꾼 시각") LocalDateTime processedAt
) {

    public static CareRecordResponse of(CareRecord record) {
        OrganizationMember processedBy = record.getProcessedBy();
        CareRecord current = record.getRecipient().getCurrentRecord();
        return new CareRecordResponse(record.getPublicId(), record.getRecipient().getPublicId(),
                record.getStatusCode(), record.getReasonType(), record.getReasonMessage(), record.getDetectedAt(),
                record.getProcessingStatus(), current != null && current.getId().equals(record.getId()),
                processedBy == null ? null : processedBy.getAccount().getName(), record.getProcessedAt());
    }
}
