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
    @Schema(description = "6개 대 감정별 비율 및 탐색 질문 목록")
    private final List<EmotionBreakdownItem> breakdown;
    @Schema(description = "연결된 챗봇 세션 ID (chatStatus=ready 일 때 유효)", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String chatSessionId;
    @Schema(description = "챗봇 세션 상태 (pending: 생성 전·중 / ready: 준비 완료 / failed: 생성 실패)", example = "ready")
    private final String chatStatus;
}
