package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "감정 피드백 요청 — AI 분석 감정에 대한 사용자 실제 반응")
public record FeedbackRequest(
        @Schema(description = "사용자가 선택한 실제 감정 (소문자 문자열: happy/sad/neutral/angry/anxiety/surprise)", example = "happy")
        @NotBlank String emotion,
        @Schema(description = "상세 설명 (선택)", example = "생각보다 기쁜 감정이었어요")
        String detail
) {}
