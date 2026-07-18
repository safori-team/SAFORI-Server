package com.safori.domain.chatbot.policy;

import com.safori.domain.emotion.entity.EmotionType;

import java.time.LocalDate;

/**
 * 하루치 마음일기의 대표 감정. 마음일기는 하루 1건 정책이므로 날짜당 1건이다.
 *
 * @param date      일기 작성 날짜
 * @param voiceId   일기(Voice) ID
 * @param emotion   {@link com.safori.domain.emotion.service.EmotionResolver}를 통과한 대표 감정
 *                  (사용자 신고가 있으면 신고 감정, 없으면 AI 분석 감정). 분석 전이면 null.
 */
public record DailyDiaryEmotion(
        LocalDate date,
        Long voiceId,
        EmotionType emotion
) {}
