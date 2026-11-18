package com.safori.api.operator.dto;

import com.safori.api.chatbot.dto.MindDiaryOfferResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "[개발용] 상담 제안 즉시 평가 결과")
public record TriggerMindDiaryOfferResponse(
        @Schema(description = "이번 호출로 제안을 새로 만들었는지") boolean offered,
        @Schema(description = "제안을 만든 일기, 만들지 못했으면 마지막으로 평가한 최신 일기. 후보가 없으면 null",
                example = "16") Long voiceId,
        @Schema(description = """
                판정 코드. 만들었으면 제안 근거(예: CONSECUTIVE_NEGATIVE_3D_STREAK_3),
                못 만들었으면 최신 일기의 보류 사유(예: STREAK_TOO_SHORT_2, NOT_NEGATIVE).
                평가할 일기가 없으면 NO_CANDIDATE, 다른 경로(스케줄러)가 먼저 제안했으면 ALREADY_TRIGGERED.
                """, example = "CONSECUTIVE_NEGATIVE_3D_STREAK_3") String reason,
        @Schema(description = "호출 후 대기 중인 상담 제안 (앱 모달 기준)") MindDiaryOfferResponse offer
) {
}
