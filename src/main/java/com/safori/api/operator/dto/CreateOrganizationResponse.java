package com.safori.api.operator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "생성된 기관과 최초 관리자")
public record CreateOrganizationResponse(
        @Schema(description = "기관 외부 식별자") String organizationPublicId,
        @Schema(description = "관리자 계정 외부 식별자") String adminAccountUuid
) {
}
