package com.safori.api.emergency.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "긴급 전화 기록 결과")
public record EmergencyCallResponse(
        @Schema(description = "복지관(기관)에 즉시 확인이 올라갔는지. 기관에 등록되지 않았거나 이용이 중지된 어르신이면 false", example = "true")
        boolean notified
) {
}
