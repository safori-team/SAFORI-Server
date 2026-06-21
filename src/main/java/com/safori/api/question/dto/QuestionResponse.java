package com.safori.api.question.dto;

import com.safori.domain.question.entity.QuestionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Schema(description = "질문 응답")
@Builder
@Getter
@RequiredArgsConstructor
public class QuestionResponse {
    @Schema(description = "질문 ID", example = "1")
    private final Long id;
    @Schema(description = "질문 카테고리 (enum)", example = "EMOTION")
    private final QuestionCategory questionCategory;
    @Schema(description = "카테고리 내 질문 순서 (0부터 시작)", example = "0")
    private final Integer questionIndex;
    @Schema(description = "질문 내용", example = "오늘 가장 기억에 남는 순간은 무엇인가요?")
    private final String content;
}
