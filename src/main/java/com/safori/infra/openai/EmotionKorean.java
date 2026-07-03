package com.safori.infra.openai;

import com.safori.domain.emotion.entity.EmotionType;

/**
 * 6대 감정(EmotionType) → 한글 표기.
 * 고연령자 대상 서비스라 AI 리포트는 감정을 반드시 한국어로 서술해야 하므로
 * 프롬프트에 감정 데이터를 한글로 주입할 때 사용한다.
 */
final class EmotionKorean {

    private EmotionKorean() {
    }

    static String of(EmotionType emotionType) {
        if (emotionType == null) return null;
        return switch (emotionType) {
            case HAPPY -> "즐거움";
            case SAD -> "슬픔";
            case NEUTRAL -> "안정";
            case ANGRY -> "분노";
            case ANXIETY -> "불안";
            case SURPRISE -> "놀람";
        };
    }
}
