package com.safori.api.recipient.dto;

import com.safori.api.common.dto.PagedResponse;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "대상자 현황·목록. 탭 개수와 현재 탭의 페이지를 함께 준다.")
public record RecipientListResponse(
        @Schema(description = "탭 개수 (검색어·담당자 필터 적용, 상태 코드·배정 탭과 무관)") Counts counts,
        @Schema(description = "현재 탭의 대상자 페이지") PagedResponse<Item> recipients
) {

    public record Counts(
            @Schema(description = "전체", example = "48") long total,
            @Schema(description = "배정", example = "45") long assigned,
            @Schema(description = "미배정", example = "3") long unassigned,
            @Schema(description = "즉시 확인", example = "1") long urgent,
            @Schema(description = "주의", example = "2") long caution,
            @Schema(description = "관심", example = "1") long interest) {
    }

    public record Item(
            @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
            @Schema(description = "이름", example = "홍길동") String name,
            @Schema(description = "생년월일. 없으면 null", example = "1960-03-12") LocalDate birthDate,
            @Schema(description = "상태 코드. null이면 표시 없음(X) — '현재 등록된 확인 사유가 없어요'") CareStatusCode statusCode,
            @Schema(description = "사유 문구. 상태 코드가 없으면 null", example = "담당자와의 연결을 요청했어요.") String reasonMessage,
            @Schema(description = "처리 상태 (UNCHECKED=미확인, IN_PROGRESS=조치중). 상태 코드가 없으면 null") CareProcessingStatus processingStatus,
            @Schema(description = "요청·감지 시각 ('20분 전 요청' 표시용)") LocalDateTime detectedAt,
            @Schema(description = "현재 기록 식별자. 기록 상세 경로의 {recordId}") String recordId,
            @Schema(description = "현재 담당자. 미배정이면 null") Manager manager) {
    }

    public record Manager(
            @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
            @Schema(description = "이름", example = "박지현") String name) {
    }
}
