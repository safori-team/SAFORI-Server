package com.safori.domain.question.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// 프론트엔드 계약: 질문 카테고리 집합. 추가/삭제/이름변경은 클라이언트와 합의 후에만 허용된다.
class QuestionCategoryTest {

    @Test
    @DisplayName("QuestionCategory는 정확히 5종이며 집합이 고정되어 있다")
    void valueSet_isExactlyFiveCategories() {
        assertThat(Arrays.stream(QuestionCategory.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "EMOTION", "STRESS", "PHYSICAL", "SOCIAL", "SELF_REFLECTION");
        assertThat(QuestionCategory.values()).hasSize(5);
    }
}
