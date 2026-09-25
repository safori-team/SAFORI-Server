package com.safori.api.recipient.dto;

import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareReasonType;
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
        @Schema(description = "사유 종류") CareReasonType reasonType,
        @Schema(description = "사유 제목", example = "동일 감정 반복") String reasonTitle,
        @Schema(description = "사유 설명", example = "최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.") String reasonMessage,
        @Schema(description = "안내 라벨 (관심 정보 / 확인 권장 / 필요한 조치)", example = "확인 권장") String guidanceLabel,
        @Schema(description = "안내 문구", example = "다음 연락이나 방문 시 최근 기분에 달라진 점이 있는지 살펴봐 주세요.") String guidance,
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
                record.getStatusCode(), record.getReasonType(), record.getReasonType().title(), record.getReasonMessage(),
                record.getStatusCode().guidanceLabel(), record.getReasonType().guidance(), record.getDetectedAt(),
                record.getProcessingStatus(), current != null && current.getId().equals(record.getId()),
                processedBy == null ? null : processedBy.getAccount().getName(), record.getProcessedAt());
    }
}
