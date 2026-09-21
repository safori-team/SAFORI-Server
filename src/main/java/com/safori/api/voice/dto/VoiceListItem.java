package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.Voice;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 목록 항목")
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceListItem {
    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "일기 작성 날짜", example = "2024-01-15")
    private final LocalDate createdAt;
    @Schema(description = "감정 분석 상태 (PROCESSING / COMPLETED / FAILED)", example = "COMPLETED")
    private final Voice.AnalysisStatus analysisStatus;
    @Schema(description = "대표 감정 (분석 완료 전 null)", example = "HAPPY")
    private final EmotionType emotion;
    @Schema(description = "연결된 질문 제목 (질문 없으면 null)", example = "오늘 가장 기억에 남는 순간은?")
    private final String questionTitle;
    @Schema(description = "STT 변환 텍스트 (분석 완료 전 null)", example = "오늘 친구와 오랜만에 만나서 정말 즐거웠어요.")
    private final String content;
}
