package com.safori.api.operator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기관 관리자 교체 응답")
public record ReplaceOrganizationAdminResponse(
        @Schema(description = "새 관리자 계정 UUID") String adminAccountUuid
) {
}
