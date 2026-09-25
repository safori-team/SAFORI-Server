package com.safori.api.journal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "최근 조치 기록 카드. 누르면 일지 상세로 간다.")
public record JournalSummary(
        @Schema(description = "일지 식별자") String journalId,
        @Schema(description = "확인 일시") LocalDateTime confirmedAt,
        @Schema(description = "작성자 이름", example = "박지현") String writerName,
        @Schema(description = "확인 방식", example = "방문") String method,
        @Schema(description = "확인 결과", example = "연락됨") String result,
        @Schema(description = "수행한 조치", example = "[\"안부 확인 완료\"]") List<String> actions,
        @Schema(description = "필요한 후속 조치", example = "[\"보호자 연락 필요\"]") List<String> followUps
) {
}
