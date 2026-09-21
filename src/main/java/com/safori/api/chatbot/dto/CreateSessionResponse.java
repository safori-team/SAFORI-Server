package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "챗봇 세션 생성 응답")
public record CreateSessionResponse(
        @Schema(description = "생성된 세션 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId
) {}
