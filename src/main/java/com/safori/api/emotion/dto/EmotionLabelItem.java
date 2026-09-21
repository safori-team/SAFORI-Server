package com.safori.api.emotion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "버블차트 단일 감정 레이블 항목")
@Getter
@Builder
@AllArgsConstructor
public class EmotionLabelItem {
    @Schema(description = "세부 감정 레이블 (영문 소문자, 드릴다운 필터 키)", example = "joy")
    private final String label;
    @Schema(description = "세부 감정 레이블 한글 표시명", example = "기쁨")
    private final String labelKr;
    @Schema(description = "대 감정 카테고리", example = "happy")
    private final String category;
    @Schema(description = "해당 월에 이 감정이 등장한 일기 수 (버블 크기 기준)", example = "5")
    private final long diaryCount;
    @Schema(description = "평균 intensity × 1000 (버블 가중치 참고용)", example = "7523")
    private final int avgIntensityX1000;
}
