package com.safori.api.recipient.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기관 등록 전 어르신 조회 결과. 본인 확인용으로 개인정보는 마스킹한다.")
public record RecipientLookupResponse(
        @Schema(description = "조회한 아이디", example = "elder01") String loginId,
        @Schema(description = "이름 (마스킹)", example = "김*수") String name,
        @Schema(description = "휴대폰 번호 (마스킹). 없으면 null", example = "010-****-5678") String phone,
        @Schema(description = "생년월일 (연도만). 없으면 null", example = "1960-**-**") String birthDate,
        @Schema(description = "이미 어느 기관에 등록돼 있으면 true. 어르신은 한 기관에만 등록된다", example = "false")
        boolean registered
) {
}
