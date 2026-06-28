package com.safori.api.voice.dto;

import com.safori.domain.voice.entity.Voice;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 · 챗봇 세션 처리 상태 응답")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class VoiceDiaryStatusResponse {

    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "감정 분석 상태 (PENDING / PROCESSING / COMPLETED / FAILED)", example = "COMPLETED")
    private final Voice.AnalysisStatus diaryStatus;
    @Schema(description = "챗봇 세션 생성 상태 (pending / ready / failed). 일기 분석 완료 전이면 pending", example = "ready")
    private final String chatStatus;
    @Schema(description = "챗봇 세션 ID (chatStatus=COMPLETED 일 때 유효)", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String sessionId;
    @Schema(description = "실패 시 오류 메시지 (FAILED 상태일 때만 포함)", example = "AI 분석 중 오류가 발생했습니다")
    private final String errorMessage;
}
