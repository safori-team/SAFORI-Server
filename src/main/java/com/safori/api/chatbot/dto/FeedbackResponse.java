package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "감정 피드백 응답")
public record FeedbackResponse(
        @Schema(description = "메시지 ID", example = "101")
        Long messageId,
        @Schema(description = "피드백 감정", example = "공감됨")
        String feedbackEmotion,
        @Schema(description = "피드백 상세 설명", example = "정확히 제가 느낀 감정이에요.")
        String feedbackDetail,
        @Schema(description = "피드백 입력 시각", example = "2024-01-15T09:35:00")
        LocalDateTime feedbackAt
) {}
