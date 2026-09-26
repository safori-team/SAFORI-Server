package com.safori.api.journal.dto;

import com.safori.api.recipient.dto.RecipientDetailResponse;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "일지 상세. 섹션별로 고른 항목(작성 당시 문구)을 준다.")
public record JournalDetailResponse(
        @Schema(description = "일지 식별자") String journalId,
        @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
        @Schema(description = "대상자 이름", example = "김영희") String recipientName,
        @Schema(description = "작성자 이름", example = "박지현") String writerName,
        @Schema(description = "작성 당시 확인 필요도. 없었으면 null") CareStatusCode statusCode,
        @Schema(description = "작성 당시 처리 상태. 없었으면 null") CareProcessingStatus processingStatus,
        @Schema(description = "작성 당시 확인 사유 (제목·문구·안내). 확인 사유가 없을 때 쓴 일지면 null") RecipientDetailResponse.Reason reason,
        @Schema(description = "확인 일시 (담당자 입력)") LocalDateTime confirmedAt,
        @Schema(description = "작성 일시 (시스템)") LocalDateTime writtenAt,
        @Schema(description = "보호자 공개 여부") boolean guardianVisible,
        @Schema(description = "간단 메모") String memo,
        @Schema(description = "섹션별 고른 항목 (섹션 순서, 고른 게 없는 섹션은 빠진다)") List<Section> sections
) {

    public record Section(
            @Schema(description = "섹션 코드", example = "CONDITION") String code,
            @Schema(description = "섹션 이름", example = "대상자 상태") String label,
            List<Item> items) {
    }

    public record Item(
            @Schema(description = "항목 코드", example = "EMOTION_CHANGE") String code,
            @Schema(description = "항목 이름 (작성 당시)", example = "정서 변화") String label,
            @Schema(description = "직접 입력값 (기타)") String text,
            @Schema(description = "고른 하위 항목") List<Item> children) {
    }
}
