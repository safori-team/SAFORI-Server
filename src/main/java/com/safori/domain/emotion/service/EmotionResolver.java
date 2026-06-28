package com.safori.domain.emotion.service;

import com.safori.domain.emotion.entity.EmotionType;

/**
 * 마음일기 조회 시 노출할 "대표 감정" 결정 규칙.
 *
 * <p>사용자가 AI 분석 결과를 신고(reportedEmotion)한 경우 그 값을 우선 반영하고,
 * 신고가 없으면(null) AI 분석 대표 감정을 사용한다.
 *
 * <p>모든 마음일기 조회 경로(목록/상세/분석 등)는 대표 감정 노출 시
 * 이 규칙을 통해 결정해 정책을 한 곳에 모은다.
 */
public final class EmotionResolver {

    private EmotionResolver() {}

    /**
     * @param aiTopEmotion AI 분석 대표 감정 (VoiceComposite.topEmotion, nullable)
     * @param reportedEmotion 사용자 신고 감정 (없으면 null)
     * @return 신고가 있으면 신고 감정, 없으면 AI 대표 감정
     */
    public static EmotionType effectiveTopEmotion(EmotionType aiTopEmotion, EmotionType reportedEmotion) {
        return reportedEmotion != null ? reportedEmotion : aiTopEmotion;
    }
}
