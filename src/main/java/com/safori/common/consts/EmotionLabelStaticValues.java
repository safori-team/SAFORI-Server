package com.safori.common.consts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private EmotionLabelStaticValues() {
    }

    /**
     * 영문 label을 한글 표시명으로 변환. 매핑에 없으면 원본을 그대로 반환한다.
     */
    public static String toKorean(String label) {
        if (label == null) return null;
        return LABEL_KO.getOrDefault(label, label);
    }
}
