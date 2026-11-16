package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "담당자 기본 정보")
public record WorkerProfileResponse(
        @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
        @Schema(description = "아이디", example = "worker01") String loginId,
        @Schema(description = "이름", example = "박담당") String name,
        @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone,
        @Schema(description = "직종", example = "생활복지사") String jobTitle,
        @Schema(description = "계정 활성 여부", example = "true") boolean active
) {
}
