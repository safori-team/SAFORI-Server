package com.safori.api.guardian.dto;

import com.safori.domain.care.entity.GuardianRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "보호자 상세")
public record GuardianDetailResponse(
        @Schema(description = "보호자 식별자 (계정 UUID)") String guardianId,
        @Schema(description = "아이디", example = "guard01") String loginId,
        @Schema(description = "이름", example = "김희영") String name,
        @Schema(description = "연락처 (숫자만)", example = "01012345678") String phone,
        @Schema(description = "계정 활성 여부", example = "true") boolean active,
        @Schema(description = "가입일 (계정 생성 일시)") LocalDateTime joinedAt,
        @Schema(description = "연결 대상자. 없으면 null — '연결된 대상자 없습니다'") LinkedRecipient careRecipient
) {

    public record LinkedRecipient(
            @Schema(description = "대상자 식별자 (public_id)") String careRecipientId,
            @Schema(description = "이름", example = "김영희") String name,
            @Schema(description = "생년월일. 없으면 null", example = "1960-03-12") LocalDate birthDate,
            @Schema(description = "관계", example = "CHILD") GuardianRelation relation,
            @Schema(description = "관계가 기타일 때 입력값") String relationText,
            @Schema(description = "화면 표시용 관계 (기타면 입력값)", example = "자녀") String relationLabel,
            @Schema(description = "연결일") LocalDateTime linkedAt) {
    }
}
