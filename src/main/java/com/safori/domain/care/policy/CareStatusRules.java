package com.safori.domain.care.policy;

import com.safori.domain.emotion.entity.EmotionType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 단계별 확인 방식의 판정 계산(순수 함수). 데이터 조회와 기록은 {@link CareStatusDetector}가 한다.
 * 반환값은 기록에 저장할 설명 문장이다(조건을 만족하지 않으면 empty).
 */
public final class CareStatusRules {

    /** 최신 몇 건(회)을 보는지. */
    public static final int LATEST = 3;
    /** 그중 몇 건(회) 이상이면 발생하는지. */
    public static final int THRESHOLD = 2;
    /** 평소 작성 간격을 계산할 최근 일기 수. */
    public static final int INTERVAL_SAMPLE = 10;
    /** 평소 작성 간격을 계산하는 최소 일기 수(간격 2개). */
    public static final int INTERVAL_MIN_DIARIES = 3;

    private static final Map<EmotionType, String> NEGATIVE = Map.of(
            EmotionType.SAD, "슬픔", EmotionType.ANXIETY, "불안", EmotionType.ANGRY, "분노");

    private CareStatusRules() {
    }

    /**
     * 동일 감정 반복. 최신 일기(최대 3건)의 대표 감정 중 같은 부정 감정이 2건 이상.
     *
     * @param latestEmotions 최신순 대표 감정(최대 3건)
     */
    public static Optional<String> sameEmotion(List<EmotionType> latestEmotions) {
        Map<EmotionType, Integer> counts = new EnumMap<>(EmotionType.class);
        latestEmotions.stream().filter(NEGATIVE::containsKey).forEach(e -> counts.merge(e, 1, Integer::sum));
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= THRESHOLD)
                .max(Map.Entry.comparingByValue())
                .map(entry -> "최근 일기 %d건 중 %d건에서 %s 계열 감정이 반복됐어요."
                        .formatted(latestEmotions.size(), entry.getValue(), NEGATIVE.get(entry.getKey())));
    }

    /**
     * 추가 상담 반복 이용. 최신 상담(최대 3회) 중 '조금 더 이야기하기'를 2회 이상 선택.
     *
     * @param latestExtended 최신순 상담별 연장 여부(최대 3회)
     */
    public static Optional<String> counselExtension(List<Boolean> latestExtended) {
        long extended = latestExtended.stream().filter(Boolean::booleanValue).count();
        if (extended < THRESHOLD) {
            return Optional.empty();
        }
        return Optional.of("최근 상담 %d회 중 %d회에서 '조금 더 이야기하기'를 선택했어요."
                .formatted(latestExtended.size(), extended));
    }

    /**
     * 작성 주기 감소. 평소 간격(최근 일기 간격의 중앙값)의 2배 이상이 지남.
     *
     * @param diaryDates 최신순 일기 작성일(최대 10건)
     * @param base       경과를 재는 기준일(마지막 일기일과 이 사유 마지막 완료일 중 늦은 날)
     */
    public static Optional<String> diaryInterval(List<LocalDate> diaryDates, LocalDate base, LocalDate today) {
        if (diaryDates.size() < INTERVAL_MIN_DIARIES) {
            return Optional.empty();
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 0; i + 1 < diaryDates.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(diaryDates.get(i + 1), diaryDates.get(i)));
        }
        gaps.sort(null);
        // 중앙값: 한 번 길게 비운 간격이 평소 간격을 부풀리지 않게. 하루에 여러 번 쓴 경우를 위해 최소 1일.
        long usual = Math.max(1, gaps.get(gaps.size() / 2));
        long elapsed = ChronoUnit.DAYS.between(base, today);
        if (elapsed < usual * 2) {
            return Optional.empty();
        }
        return Optional.of("평소 %d일이던 마음일기 작성 간격이 현재 %d일로 길어졌어요.".formatted(usual, elapsed));
    }
}
