package com.safori.common.consts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionLabelStaticValuesTest {

    @Test
    @DisplayName("매핑된 label - 한글 반환")
    void toKorean_mapped() {
        assertThat(EmotionLabelStaticValues.toKorean("joy")).isEqualTo("기쁨");
        assertThat(EmotionLabelStaticValues.toKorean("anxiety")).isEqualTo("불안");
        assertThat(EmotionLabelStaticValues.toKorean("surprise_positive")).isEqualTo("긍정적 놀람");
    }

    @Test
    @DisplayName("목록 밖 label - 원본(영문) 그대로 반환")
    void toKorean_unmapped_returnsInput() {
        assertThat(EmotionLabelStaticValues.toKorean("unknown_label")).isEqualTo("unknown_label");
    }

    @Test
    @DisplayName("null - null 반환")
    void toKorean_null() {
        assertThat(EmotionLabelStaticValues.toKorean(null)).isNull();
    }

    @Test
    @DisplayName("프롬프트 대응표와 동일하게 48개 label 매핑")
    void mappingCount_is48() {
        assertThat(EmotionLabelStaticValues.LABEL_KO).hasSize(48);
    }
}
