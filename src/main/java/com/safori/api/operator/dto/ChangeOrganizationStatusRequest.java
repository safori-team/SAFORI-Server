package com.safori.api.operator.dto;

import com.safori.domain.organization.entity.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "기관 상태 변경 요청")
public record ChangeOrganizationStatusRequest(
        @Schema(description = "ACTIVE(활성) / INACTIVE(비활성: 소속 구성원 전원이 다음 요청부터 권한을 잃음)", example = "INACTIVE")
        @NotNull
        OrganizationStatus status
) {
}
