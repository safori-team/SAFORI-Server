package com.safori.domain.chatbot.policy;

import com.safori.domain.emotion.entity.EmotionType;

import java.time.LocalDate;

/**
 * 세션 생성 여부를 판단하기 위한 입력. 트리거 후보가 된 마음일기 1건을 가리킨다.
 *
 * @param userId      일기 작성자
 * @param voiceId     트리거 후보 일기
 * @param diaryDate   일기 작성 날짜
 * @param topEmotion  트리거 후보 일기의 대표 감정 (신고 감정 우선 적용)
 */
public record SessionTriggerContext(
        Long userId,
        Long voiceId,
        LocalDate diaryDate,
        EmotionType topEmotion
) {}
