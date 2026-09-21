package com.safori.api.emotion.dto;

import com.safori.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "월별 감정 분석 종합 응답")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class MonthlyAnalysisCombinedResponse {

    @Schema(description = "대 감정별 일기 등록 횟수 (EmotionType → 개수)")
    private final Map<EmotionType, Long> monthlyEmotionCounts;
    @Schema(description = "해당 월 대표 감정", example = "HAPPY")
    private final EmotionType topEmotion;
    @Schema(description = "해당 월 전체 일기 수", example = "12")
    private final long totalCount;
    @Schema(description = "AI 생성 월간 감정 리포트 메시지", example = "이번 달은 행복한 감정을 자주 느끼셨군요!")
    private final String reportMessage;
}
