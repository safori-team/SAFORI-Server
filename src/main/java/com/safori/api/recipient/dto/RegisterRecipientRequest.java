package com.safori.api.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "어르신을 요청한 구성원의 기관에 대상자로 등록")
public record RegisterRecipientRequest(
        @Schema(description = "어르신 앱 아이디 (조회에 쓴 값)", example = "elder01") @NotBlank String loginId
) {
}
