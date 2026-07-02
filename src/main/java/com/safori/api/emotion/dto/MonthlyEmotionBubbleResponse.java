package com.safori.api.emotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "특정 월의 세부 감정 버블차트 응답")
@Getter
@Builder
@AllArgsConstructor
public class MonthlyEmotionBubbleResponse {
    @Schema(description = "조회 연월 (yyyy-MM 형식)", example = "2024-01")
    private final String yearMonth;
    @Schema(description = "세부 감정 레이블 목록 (버블 크기 기준 정렬)")
    private final List<EmotionLabelItem> labels;
}
