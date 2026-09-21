package com.safori.domain.chatbot.model;

import com.safori.domain.emotion.entity.EmotionType;

import java.util.List;

/**
 * 챗봇 음성(STT + 감정) 분석 결과 중 상담 프롬프트에 실제로 쓰이는 부분만 추린 다이제스트.
 * 마음일기의 거대한 {@code VoiceComposite} 대신, 챗봇이 소비하는 최소 집합만 담는다.
 *
 * <ul>
 *   <li>{@code topEmotion} — 대표 감정 (6개 대분류)</li>
 *   <li>{@code topEmotionConfidenceBps} — 대표 감정 비중 (bps, 0~10000)</li>
 *   <li>{@code labels} — intensity 상위 N개 세부 감정 레이블</li>
 * </ul>
 */
public record VoiceEmotionDigest(
        EmotionType topEmotion,
        int topEmotionConfidenceBps,
        List<VoiceEmotionLabelView> labels
) {}
