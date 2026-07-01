package com.safori.api.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "세부 감정 분석 항목 (세부 감정 1개의 비율)")
@Getter
@Builder
@AllArgsConstructor
public class SubEmotionItem {

    @Schema(description = "세부 감정 한글 레이블", example = "기쁨")
    private final String label;
    @Schema(description = "전체 세부 감정 대비 비율 (0.0 ~ 100.0, 소수점 1자리)", example = "35.7")
    private final double percentage;
    @Schema(description = "해당 감정 카테고리에 대응되는 탐색 질문", example = "오늘 행복한 감정의 원천은 무엇이었나요?")
    private final String question;
}
