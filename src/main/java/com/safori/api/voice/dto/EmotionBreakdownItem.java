package com.safori.api.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "감정 분석 breakdown 항목 (대 감정 1개의 비율과 탐색 질문)")
@Getter
@Builder
@AllArgsConstructor
public class EmotionBreakdownItem {

    @Schema(description = "감정 한글 레이블", example = "행복")
    private final String label;
    @Schema(description = "전체 대비 비율 (0.0 ~ 100.0, 소수점 1자리)", example = "42.5")
    private final double percentage;
    @Schema(description = "해당 감정에 대응되는 탐색 질문", example = "오늘 행복했던 순간이 무엇이었나요?")
    private final String question;
}
