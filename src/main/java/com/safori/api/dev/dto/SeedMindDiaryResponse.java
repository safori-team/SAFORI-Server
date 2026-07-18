package com.safori.api.dev.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "[개발용] 마음일기 시딩 결과")
public record SeedMindDiaryResponse(
        @Schema(description = "생성된 voiceId", example = "1001")
        Long voiceId,
        @Schema(description = "설정된 작성일 (스트릭 판정 기준)", example = "2026-07-15")
        LocalDate diaryDate,
        @Schema(description = "최종 대표 감정 (덮어썼으면 지정값, 아니면 분석값)", example = "SAD")
        String topEmotion,
        @Schema(description = "감정을 덮어썼는지 여부", example = "true")
        boolean emotionOverridden
) {}
