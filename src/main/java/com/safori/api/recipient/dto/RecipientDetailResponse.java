package com.safori.api.recipient.dto;

import com.safori.api.journal.dto.JournalSummary;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "대상자 상세 (현황 카드를 누르면 나오는 기록 상세 화면)")
public record RecipientDetailResponse(
        @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
        @Schema(description = "이름", example = "김영희") String name,
        @Schema(description = "생년월일. 없으면 null", example = "1960-03-12") LocalDate birthDate,
        @Schema(description = "현재 담당자. 미배정이면 null") RecipientListResponse.Manager manager,
        @Schema(description = "확인 필요도(상태 코드). null이면 표시 없음(X)") CareStatusCode statusCode,
        @Schema(description = "처리 상태. 상태 코드가 없으면 null") CareProcessingStatus processingStatus,
        @Schema(description = "확인 사유. 상태 코드가 있을 때만 있다 (없으면 null)") Reason reason,
        @Schema(description = "최근 조치 기록 (일지, 확인 일시 최신순 최대 20건). 카드를 누르면 일지 상세") List<JournalSummary> recentActions
) {

    public record Reason(
            @Schema(description = "현재 기록 식별자. 처리 상태 변경 경로의 {recordId}") String recordId,
            @Schema(description = "사유 종류") CareReasonType reasonType,
            @Schema(description = "사유 제목", example = "동일 감정 반복") String title,
            @Schema(description = "사유 설명", example = "최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.") String message,
            @Schema(description = "안내 라벨", example = "확인 권장") String guidanceLabel,
            @Schema(description = "안내 문구", example = "다음 연락이나 방문 시 최근 기분에 달라진 점이 있는지 살펴봐 주세요.") String guidance,
            @Schema(description = "요청·감지 시각") LocalDateTime detectedAt) {
    }
}
