package com.safori.api.journal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "일지 등록. 고른 항목 코드를 평평한 목록으로 보낸다(하위 항목도 같은 목록에).")
public record WriteJournalRequest(
        @Schema(description = "확인 일시 (담당자 입력)", example = "2026-09-13T14:00:00")
        @NotNull LocalDateTime confirmedAt,

        @Schema(description = "고른 항목. 규칙은 GET /v1/api/admin/journal-form 참고")
        @NotNull @Valid List<Selection> selections,

        @Schema(description = "간단 메모 (선택)", example = "다음 주 재방문 예정")
        @Size(max = 1000) String memo,

        @Schema(description = "보호자 공개 여부", example = "false")
        @NotNull Boolean guardianVisible
) {

    public record Selection(
            @Schema(description = "항목 코드", example = "VISIT") @NotBlank String optionCode,
            @Schema(description = "직접 입력 항목(기타)일 때만 입력값") @Size(max = 200) String text) {
    }
}
