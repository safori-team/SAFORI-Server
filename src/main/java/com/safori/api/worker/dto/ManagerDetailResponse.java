package com.safori.api.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "담당자 상세")
public record ManagerDetailResponse(
        @Schema(description = "담당자 식별자 (계정 UUID)") String managerId,
        @Schema(description = "아이디", example = "worker01") String loginId,
        @Schema(description = "이름", example = "김철수") String name,
        @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone,
        @Schema(description = "직종", example = "사회복지사") String jobTitle,
        @Schema(description = "계정 활성 여부", example = "true") boolean active,
        @Schema(description = "소속기관 이름", example = "행복복지관") String organizationName,
        @Schema(description = "역할", example = "CARE_WORKER") String role,
        @Schema(description = "현재 배정 인원", example = "10") int assignedCount,
        @Schema(description = "현재 배정 대상자 (이름순)") List<AssignedRecipient> assignedRecipients
) {

    public record AssignedRecipient(
            @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
            @Schema(description = "이름", example = "홍길동") String name,
            @Schema(description = "생년월일. 없으면 null", example = "1960-03-12") LocalDate birthDate) {
    }
}
