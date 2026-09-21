package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "CBT 리프레이밍 메시지 전송 요청")
public record ReframingRequest(
        @Schema(description = "채팅 세션 ID (POST /sessions에서 발급)", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank String sessionId,
        @Schema(description = "사용자 입력 메시지", example = "오늘 친구와 다퉜어요")
        @NotBlank String userInput,
        @Schema(description = "현재 감정 (선택, EmotionType 값)", example = "SADNESS")
        String emotion
) {}
