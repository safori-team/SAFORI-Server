package com.safori.infra.openai;

import com.safori.domain.emotion.entity.EmotionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionKoreanTest {

    @Test
    @DisplayName("6대 감정 한글 매핑")
    void of_mapsAllEmotions() {
        assertThat(EmotionKorean.of(EmotionType.HAPPY)).isEqualTo("즐거움");
        assertThat(EmotionKorean.of(EmotionType.SAD)).isEqualTo("슬픔");
        assertThat(EmotionKorean.of(EmotionType.NEUTRAL)).isEqualTo("안정");
        assertThat(EmotionKorean.of(EmotionType.ANGRY)).isEqualTo("분노");
        assertThat(EmotionKorean.of(EmotionType.ANXIETY)).isEqualTo("불안");
        assertThat(EmotionKorean.of(EmotionType.SURPRISE)).isEqualTo("놀람");
    }

    @Test
    @DisplayName("null → null")
    void of_null() {
        assertThat(EmotionKorean.of(null)).isNull();
    }
}
