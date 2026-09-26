package com.safori.domain.care.policy;

import com.safori.domain.emotion.entity.EmotionType;

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
}
