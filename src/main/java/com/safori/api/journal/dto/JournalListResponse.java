package com.safori.api.journal.dto;

import com.safori.api.common.dto.PagedResponse;
import com.safori.domain.care.entity.CareStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "일지 목록")
public record JournalListResponse(
        @Schema(description = "적용된 조회 시작일 (확인 일시 기준)", example = "2026-09-01") LocalDate from,
        @Schema(description = "적용된 조회 종료일 (이날 포함)", example = "2026-09-30") LocalDate to,
        @Schema(description = "일지 페이지. 전체 건수는 totalElements") PagedResponse<Item> journals
) {

    public record Item(
            @Schema(description = "일지 식별자. 일지 상세 경로의 {journalId}") String journalId,
            @Schema(description = "대상자 식별자. 일지 상세 경로의 {careRecipientId}") String careRecipientId,
            @Schema(description = "대상자 이름", example = "홍길동") String recipientName,
            @Schema(description = "확인 일시") LocalDateTime confirmedAt,
            @Schema(description = "작성 당시 확인 필요도. 없었으면 null") CareStatusCode statusCode,
            @Schema(description = "확인 방식", example = "방문") String method,
            @Schema(description = "확인 결과", example = "연락됨") String result,
            @Schema(description = "작성자 이름", example = "박지현") String writerName) {
    }
}
