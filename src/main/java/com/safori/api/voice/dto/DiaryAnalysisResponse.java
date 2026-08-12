package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 감정 분석 결과 응답")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class DiaryAnalysisResponse {

    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "대표 감정 (비율이 가장 높은 감정)", example = "HAPPY")
    private final EmotionType topEmotion;
    @Schema(description = "도란이 말풍선용 감정 요약 텍스트", example = "오늘 하루 행복한 감정을 많이 느끼셨군요!")
    private final String summary;
    @Schema(description = "6대 감정별 비율 및 탐색 질문 (비율 내림차순, 합 ≈ 100)")
    private final List<MajorEmotionItem> majorEmotions;
    @Schema(description = "세부 감정 상위 6개 비율 (전체 세부 감정 대비, 비율 내림차순)")
    private final List<SubEmotionItem> subEmotions;
}
