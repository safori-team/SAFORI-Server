package com.safori.api.guardian.dto;

import com.safori.api.common.dto.PagedResponse;
import com.safori.domain.care.entity.GuardianRelation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 목록. 탭 개수와 현재 탭의 페이지를 함께 준다.")
public record GuardianListResponse(
        @Schema(description = "탭 개수 (검색어 적용, 연결 탭과 무관)") Counts counts,
        @Schema(description = "현재 탭(status)의 보호자 페이지") PagedResponse<Item> guardians
) {

    public record Counts(
            @Schema(description = "전체", example = "10") long total,
            @Schema(description = "연결", example = "9") long linked,
            @Schema(description = "미연결", example = "1") long unlinked) {
    }

    public record Item(
            @Schema(description = "보호자 식별자 (계정 UUID)") String guardianId,
            @Schema(description = "이름", example = "김희영") String name,
            @Schema(description = "계정 활성 여부", example = "true") boolean active,
            @Schema(description = "대상자 연결 여부", example = "true") boolean linked,
            @Schema(description = "관계. 미연결이면 null", example = "CHILD") GuardianRelation relation,
            @Schema(description = "화면 표시용 관계 (기타면 입력값). 미연결이면 null", example = "자녀") String relationLabel,
            @Schema(description = "연결 대상자. 미연결이면 null") LinkedRecipient careRecipient) {
    }

    public record LinkedRecipient(
            @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
            @Schema(description = "이름", example = "홍길동") String name) {
    }
}
