package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "홈화면 최근 마음일기 미리보기")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class RecentVoiceItem {
    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "일기 작성 날짜", example = "2024-01-15")
    private final LocalDate date;
    @Schema(description = "대표 감정 (분석 완료 전 null)", example = "HAPPY")
    private final EmotionType topEmotion;
    @Schema(description = "STT 변환 텍스트 (분석 완료 전 null)", example = "오늘 친구와 오랜만에 만나서 정말 즐거웠어요.")
    private final String content;
}
