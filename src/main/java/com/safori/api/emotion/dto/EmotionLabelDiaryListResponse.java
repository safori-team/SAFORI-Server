package com.safori.api.emotion.dto;

import com.safori.api.voice.dto.VoiceListItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "특정 세부 감정을 느꼈던 일기 목록 응답")
@Getter
@Builder
@AllArgsConstructor
public class EmotionLabelDiaryListResponse {
    @Schema(description = "조회 연월 (yyyy-MM 형식)", example = "2024-01")
    private final String yearMonth;
    @Schema(description = "세부 감정 레이블", example = "joy")
    private final String label;
    @Schema(description = "대 감정 카테고리", example = "happy")
    private final String category;
    @Schema(description = "해당 감정이 기록된 마음일기 목록")
    private final List<VoiceListItem> diaries;
}
