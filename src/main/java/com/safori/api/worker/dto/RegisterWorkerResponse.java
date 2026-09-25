package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "등록된 담당자")
public record RegisterWorkerResponse(
        @Schema(description = "담당자 식별자 (backoffice_account.account_uuid). 담당자 상세 등 경로의 {managerId}에 쓴다")
        String managerId,
        @Schema(description = "아이디", example = "worker01") String loginId,
        @Schema(description = "이름", example = "박담당") String name
) {
}
