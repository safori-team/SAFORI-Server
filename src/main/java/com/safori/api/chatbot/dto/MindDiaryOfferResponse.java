package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 대기 중인 마음일기 상담 제안. 프론트가 모달로 "상담을 받으시겠어요?"를 띄우는 데 쓴다.
 * 대기 제안이 없으면 {@code hasOffer=false}이고 나머지 필드는 null이다.
 */
@Schema(description = "마음일기 상담 제안 (모달용)")
public record MindDiaryOfferResponse(
        @Schema(description = "대기 중인 제안이 있는지", example = "true")
        boolean hasOffer,
        @Schema(description = "제안 ID (수락/거절 호출에 사용). 제안이 없으면 null", example = "42")
        Long offerId,
        @Schema(description = "제안 근거 코드. 제안이 없으면 null", example = "CONSECUTIVE_NEGATIVE_3D_STREAK_3")
        String reason,
        @Schema(description = "제안이 감지된 시각. 제안이 없으면 null", example = "2026-07-18T09:30:00")
        LocalDateTime offeredAt
) {
    public static MindDiaryOfferResponse none() {
        return new MindDiaryOfferResponse(false, null, null, null);
    }

    public static MindDiaryOfferResponse of(Long offerId, String reason, LocalDateTime offeredAt) {
        return new MindDiaryOfferResponse(true, offerId, reason, offeredAt);
    }
}
