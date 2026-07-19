package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "음성 기반 리프레이밍 요청 (STT + 감정분석 + 상담을 한 번에 처리)")
public record VoiceReframingRequest(
        @Schema(description = "채팅 세션 ID (POST /sessions에서 발급)", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank String sessionId,
        @Schema(description = "presigned-url로 S3 업로드 완료한 음성의 voiceKey", example = "voices/2026/05/abc123.m4a")
        @NotBlank String voiceKey
) {}
