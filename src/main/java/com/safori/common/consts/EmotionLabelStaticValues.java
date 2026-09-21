package com.safori.common.consts;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gemini 세부 감정 label(영문) → 한글 표시명 매핑.
 * label 어휘는 감정 분석 프롬프트의 category별 고정 목록(48개)과 일치한다.
 * 목록 밖 값이 들어오면 원본(영문)을 그대로 반환한다.
 */
public final class EmotionLabelStaticValues {

    public static final Map<String, String> LABEL_KO;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // neutral
        m.put("calmness", "평온");
        m.put("contemplation", "사색");
        m.put("concentration", "집중");
        m.put("interest", "흥미");
        m.put("realization", "깨달음");
        m.put("boredom", "무료함");
        m.put("tiredness", "피로");
        m.put("confusion", "혼란");
        m.put("doubt", "의심");
        m.put("nostalgia", "향수");
        // happy
        m.put("joy", "기쁨");
        m.put("ecstasy", "황홀");
        m.put("contentment", "만족");
        m.put("satisfaction", "만족감");
        m.put("amusement", "즐거움");
        m.put("excitement", "설렘");
        m.put("pride", "자부심");
        m.put("triumph", "성취감");
        m.put("relief", "안도");
        m.put("admiration", "감탄");
        m.put("adoration", "경애");
        m.put("love", "사랑");
        m.put("romance", "낭만");
        m.put("entrancement", "매혹");
        m.put("aesthetic_appreciation", "심미적 감동");
        m.put("determination", "의지");
        // sad
        m.put("sadness", "슬픔");
        m.put("distress", "고통");
        m.put("disappointment", "실망");
        m.put("guilt", "죄책감");
        m.put("shame", "수치심");
        m.put("embarrassment", "당혹");
        m.put("empathic_pain", "공감적 아픔");
        m.put("sympathy", "연민");
        m.put("loneliness", "외로움");
        // angry
        m.put("anger", "분노");
        m.put("contempt", "경멸");
        m.put("disgust", "혐오");
        m.put("frustration", "짜증");
        m.put("envy", "질투");
        m.put("craving", "갈망");
        // anxiety
        m.put("fear", "두려움");
        m.put("anxiety", "불안");
        m.put("horror", "공포");
        // surprise
        m.put("surprise_positive", "긍정적 놀람");
        m.put("surprise_negative", "부정적 놀람");
        m.put("awe", "경외");
        m.put("awkwardness", "어색함");
        LABEL_KO = Collections.unmodifiableMap(m);
    }

    /**
     * 대분류(category) → 소속 세부 감정 label 집합. Gemini 감정 분석 프롬프트의 category별
     * 고정 목록과 동일하다. category 키는 소문자.
     */
    public static final Map<String, Set<String>> CATEGORY_LABELS;

    /** label → 소속 대분류. {@link #CATEGORY_LABELS}에서 파생. */
    private static final Map<String, String> LABEL_CATEGORY;

    static {
        Map<String, Set<String>> c = new LinkedHashMap<>();
        c.put("neutral", labelSet("calmness", "contemplation", "concentration", "interest",
                "realization", "boredom", "tiredness", "confusion", "doubt", "nostalgia"));
        c.put("happy", labelSet("joy", "ecstasy", "contentment", "satisfaction", "amusement",
                "excitement", "pride", "triumph", "relief", "admiration", "adoration", "love",
                "romance", "entrancement", "aesthetic_appreciation", "determination"));
        c.put("sad", labelSet("sadness", "distress", "disappointment", "guilt", "shame",
                "embarrassment", "empathic_pain", "sympathy", "loneliness"));
        c.put("angry", labelSet("anger", "contempt", "disgust", "frustration", "envy", "craving"));
        c.put("anxiety", labelSet("fear", "anxiety", "horror"));
        c.put("surprise", labelSet("surprise_positive", "surprise_negative", "awe", "awkwardness"));
        CATEGORY_LABELS = Collections.unmodifiableMap(c);

        Map<String, String> byLabel = new LinkedHashMap<>();
        c.forEach((category, labels) -> labels.forEach(label -> byLabel.put(label, category)));
        LABEL_CATEGORY = Collections.unmodifiableMap(byLabel);
    }

    private static Set<String> labelSet(String... labels) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(labels)));
    }

    private EmotionLabelStaticValues() {
    }

    /**
     * 영문 label을 한글 표시명으로 변환. 매핑에 없으면 원본을 그대로 반환한다.
     */
    public static String toKorean(String label) {
        if (label == null) return null;
        return LABEL_KO.getOrDefault(label, label);
    }

    /**
     * 외부에서 받은 감정 코드를 표준 label 키로 정규화한다.
     * 소분류 감정 분석 Lambda는 {@code "JOY"}처럼 대문자 코드를 반환하는데, DB와 한글 매핑은
     * 소문자 키를 쓴다.
     */
    public static String normalizeLabel(String code) {
        if (code == null) return null;
        return code.trim().toLowerCase(Locale.ROOT);
    }

    /** 48개 표준 어휘에 속하는 label인지. 어휘 밖 값은 저장하지 않고 버린다. */
    public static boolean isKnownLabel(String label) {
        return label != null && LABEL_CATEGORY.containsKey(label);
    }

    /** label이 속한 대분류(category). 어휘 밖이면 null. */
    public static String categoryOf(String label) {
        if (label == null) return null;
        return LABEL_CATEGORY.get(label);
    }
}
