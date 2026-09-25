package com.safori.api.worker.dto;

import com.safori.api.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "담당자 목록. 탭 개수와 현재 탭의 페이지를 함께 준다.")
public record ManagerListResponse(
        @Schema(description = "탭 개수 (검색어 적용, 상태 필터와 무관)") Counts counts,
        @Schema(description = "현재 탭(status)의 담당자 페이지") PagedResponse<Item> managers
) {

    public record Counts(
            @Schema(description = "전체", example = "10") long total,
            @Schema(description = "활성화", example = "9") long active,
            @Schema(description = "비활성화", example = "1") long inactive) {
    }

    public record Item(
            @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
            @Schema(description = "이름", example = "김철수") String name,
            @Schema(description = "직종", example = "사회복지사") String jobTitle,
            @Schema(description = "계정 활성 여부", example = "true") boolean active,
            @Schema(description = "현재 배정 대상자 수", example = "12") long assignedCount) {
    }
}
