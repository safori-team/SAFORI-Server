package com.safori.api.emotion.dto;

import com.safori.api.common.dto.WeekDay;
import com.safori.domain.emotion.entity.EmotionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Schema(description = "요일별 감정 항목")
@Builder
@Getter
@RequiredArgsConstructor
public class WeekDayEmotion {
    @Schema(description = "날짜", example = "2024-01-15")
    private final LocalDate date;
    @Schema(description = "요일 (MON / TUE / WED / THU / FRI / SAT / SUN)", example = "MON")
    private final WeekDay weekDay;
    @Schema(description = "해당 날짜의 대표 감정 (일기 없으면 null)", example = "HAPPY")
    private final EmotionType emotionType;
    @Schema(description = "해당 날짜 마음일기의 voiceId (일기 없으면 null). 세부 조회 진입에 사용", example = "42")
    private final Long voiceId;
}
