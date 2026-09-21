package com.safori.domain.chatbot.model;

/**
 * 도란이 프롬프트에 주입할 세부 감정 레이블의 경량 뷰.
 * (label + intensity만 — 프롬프트 포매팅에 필요한 최소 필드)
 *
 * <p>마음일기 {@code VoiceEmotionLabel} 엔티티와 챗봇 음성 Flash 분석 라벨을
 * 동일한 포맷 경로({@code ChatbotMessageMapper#summarizeVoiceEmotion})로 처리하기 위한 공용 타입.</p>
 */
public record VoiceEmotionLabelView(
        String label,
        int intensityX1000
) {}
