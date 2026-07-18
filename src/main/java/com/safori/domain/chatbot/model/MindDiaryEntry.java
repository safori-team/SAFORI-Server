package com.safori.domain.chatbot.model;

/**
 * 프롬프트에 주입할 마음일기 1건.
 *
 * @param recordedAt  작성 일시 표시용 문자열
 * @param question    주제 질문 (자유 일기면 "(자유 일기)")
 * @param content     STT 텍스트 (없으면 null)
 * @param emotionDesc 감정 분석 요약 블록
 * @param emotionHint EmotionStrategies 블록 선택용 영문 카테고리 (nullable)
 */
public record MindDiaryEntry(
        String recordedAt,
        String question,
        String content,
        String emotionDesc,
        String emotionHint
) {}
