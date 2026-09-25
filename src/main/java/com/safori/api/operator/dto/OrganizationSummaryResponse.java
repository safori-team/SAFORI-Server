package com.safori.api.operator.dto;

import com.safori.domain.organization.entity.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "운영자 기관 목록 한 줄")
public record OrganizationSummaryResponse(
        @Schema(description = "기관 식별자 (public_id)") String organizationPublicId,
        @Schema(description = "기관 이름", example = "사포리 복지관") String name,
        @Schema(description = "기관 상태 (ACTIVE / INACTIVE)") OrganizationStatus status,
        @Schema(description = "기관 관리자. 없으면 null") Admin admin,
        @Schema(description = "담당자 수 (소속 종료 제외)", example = "12") long careWorkerCount,
        @Schema(description = "대상자 수 (활성)", example = "48") long recipientCount,
        @Schema(description = "생성 일시") LocalDateTime createdDate
) {

    public record Admin(
            @Schema(description = "관리자 로그인 아이디", example = "safori_admin") String loginId,
            @Schema(description = "관리자 이름", example = "홍길동") String name) {
    }
}
