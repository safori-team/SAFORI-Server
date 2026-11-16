package com.safori.api.guardian.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safori.domain.care.entity.GuardianRelation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

@Schema(description = "보호자 연결 요청")
public record LinkGuardianRequest(
        @Schema(description = "보호자 식별자 (계정 UUID)")
        @NotBlank
        String guardianId,

        @Schema(description = "대상자와의 관계", example = "CHILD")
        @NotNull
        GuardianRelation relation,

        @Schema(description = "관계가 OTHER(기타)일 때 직접 입력한 값", example = "며느리")
        @Size(max = 50)
        String relationText
) {

    @JsonIgnore
    @AssertTrue(message = "관계가 기타면 직접 입력값을 보내야 합니다")
    public boolean isRelationTextValid() {
        return relation != GuardianRelation.OTHER || StringUtils.hasText(relationText);
    }
}
