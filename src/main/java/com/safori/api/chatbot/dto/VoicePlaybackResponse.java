package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "음성 재생용 presigned GET URL 응답")
public record VoicePlaybackResponse(
        @Schema(description = "음성 파일 재생용 임시 URL (유효시간 1시간)",
                example = "https://bucket.s3.ap-northeast-2.amazonaws.com/voices/user1/uuid.m4a?X-Amz-...")
        String url,
        @Schema(description = "URL 유효시간(초)", example = "3600")
        long expiresInSeconds
) {}
