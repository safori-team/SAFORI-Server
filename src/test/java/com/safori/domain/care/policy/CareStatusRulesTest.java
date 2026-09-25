package com.safori.domain.care.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    @Test
    @DisplayName("작성 주기 감소: 평소 간격(중앙값)의 2배가 지나면 발생, 긴 공백 한 번은 평소 간격을 부풀리지 않는다")
    void diaryInterval() {
        LocalDate d = LocalDate.of(2026, 9, 1);
        List<LocalDate> every3Days = List.of(d, d.minusDays(3), d.minusDays(6), d.minusDays(9));

        assertThat(CareStatusRules.diaryInterval(every3Days, d, d.plusDays(5))).isEmpty();
        assertThat(CareStatusRules.diaryInterval(every3Days, d, d.plusDays(6)))
                .contains("평소 3일이던 마음일기 작성 간격이 현재 6일로 길어졌어요.");
        // 완료일(9/8)이 기준이면 거기서 다시 잰다
        assertThat(CareStatusRules.diaryInterval(every3Days, d.plusDays(7), d.plusDays(10))).isEmpty();

        List<LocalDate> withOneLongGap = List.of(d, d.minusDays(1), d.minusDays(11), d.minusDays(12), d.minusDays(13));
        assertThat(CareStatusRules.diaryInterval(withOneLongGap, d, d.plusDays(2)))
                .contains("평소 1일이던 마음일기 작성 간격이 현재 2일로 길어졌어요.");

        assertThat(CareStatusRules.diaryInterval(List.of(d, d.minusDays(3)), d, d.plusDays(30))).isEmpty();
    }
}
