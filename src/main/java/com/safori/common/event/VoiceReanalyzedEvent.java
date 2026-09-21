package com.safori.common.event;

/**
 * 사용자 감정 신고로 마음일기 재분석이 완료됐을 때 발행된다.
 * Chatbot 도메인이 구독해, 조건이 깨진 상담 제안/0턴 세션을 철회한다.
 *
 * @param voiceId 재분석된 일기
 * @param userId  일기 작성자 (철회 대상 재평가 범위)
 */
public record VoiceReanalyzedEvent(Long voiceId, Long userId) {}
