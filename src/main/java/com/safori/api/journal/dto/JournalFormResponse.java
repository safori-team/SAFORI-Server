package com.safori.api.journal.dto;

import com.safori.domain.care.entity.JournalOptionGroup;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "일지 폼. 섹션 순서대로, 항목은 하위 항목(children)을 몇 단계든 가질 수 있다.")
public record JournalFormResponse(List<Group> groups) {

    public record Group(
            @Schema(description = "섹션 코드", example = "CONDITION") String code,
            @Schema(description = "섹션 이름", example = "대상자 상태") String label,
            @Schema(description = "SINGLE(하나만) / MULTI(여러 개)") JournalOptionGroup.Selection selection,
            @Schema(description = "하나 이상 골라야 하는지") boolean required,
            List<Option> options) {
    }

    public record Option(
            @Schema(description = "항목 코드 (일지 등록 시 보낼 값)", example = "EMOTION_CHANGE") String code,
            @Schema(description = "항목 이름", example = "정서 변화") String label,
            @Schema(description = "고르면 같은 섹션의 다른 항목 선택 불가 (특이사항 없음)") boolean exclusive,
            @Schema(description = "고르면 직접 입력 필요 (기타)") boolean textInput,
            @Schema(description = "하위 항목. 부모를 골랐을 때만 고를 수 있다") List<Option> children) {
    }
}
