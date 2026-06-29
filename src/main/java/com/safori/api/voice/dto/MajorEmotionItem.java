package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "대 감정 분석 항목 (6대 감정 1개의 비율)")
@Getter
@Builder
@AllArgsConstructor
public class MajorEmotionItem {

    @Schema(description = "대 감정 (EmotionType)", example = "HAPPY")
    private final EmotionType emotion;
    @Schema(description = "전체 대비 비율 (0.0 ~ 100.0, 소수점 1자리)", example = "42.5")
    private final double percentage;
}
