package com.safori.domain.care.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.safori.domain.emotion.entity.EmotionType.ANGRY;
import static com.safori.domain.emotion.entity.EmotionType.ANXIETY;
import static com.safori.domain.emotion.entity.EmotionType.HAPPY;
import static com.safori.domain.emotion.entity.EmotionType.SAD;
import static org.assertj.core.api.Assertions.assertThat;

class CareStatusRulesTest {

    @Test
    @DisplayName("동일 감정 반복: 같은 부정 감정 2건 이상이면 발생, 서로 다른 부정 감정은 발생하지 않는다")
    void sameEmotion() {
        assertThat(CareStatusRules.sameEmotion(List.of(SAD, HAPPY, SAD)))
                .contains("최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.");
        assertThat(CareStatusRules.sameEmotion(List.of(SAD, ANXIETY, ANGRY))).isEmpty();
        assertThat(CareStatusRules.sameEmotion(List.of(HAPPY, HAPPY, HAPPY))).isEmpty();
        assertThat(CareStatusRules.sameEmotion(List.of(ANXIETY, ANXIETY)))
                .contains("최근 일기 2건 중 2건에서 불안 계열 감정이 반복됐어요.");
    }

    @Test
    @DisplayName("추가 상담 반복 이용: 연장 2회 이상이면 발생")
    void counselExtension() {
        assertThat(CareStatusRules.counselExtension(List.of(true, false, true)))
                .contains("최근 상담 3회 중 2회에서 '조금 더 이야기하기'를 선택했어요.");
        assertThat(CareStatusRules.counselExtension(List.of(true, false, false))).isEmpty();
    }
}
