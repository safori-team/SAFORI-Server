package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;

import java.util.List;

public interface VoiceEmotionLabelAdaptor {

    List<VoiceEmotionLabel> saveAll(List<VoiceEmotionLabel> labels);

    List<VoiceEmotionLabel> findByVoiceId(Long voiceId);

    /** 여러 voice의 레이블 일괄 조회 — 세션 트리거 배치 처리 시 N+1 방지. */
    List<VoiceEmotionLabel> findByVoiceIds(List<Long> voiceIds);

    void deleteByVoiceId(Long voiceId);

    /**
     * 특정 voice의 세부 감정 레이블을 통째로 교체한다(삭제 → 재삽입).
     * 소분류 감정 분석 응답 반영 전용 — 호출측 트랜잭션 안에서 원자적으로 처리된다.
     */
    List<VoiceEmotionLabel> replaceByVoiceId(Long voiceId, List<VoiceEmotionLabel> labels);

    /**
     * 특정 월의 label별 집계 데이터 반환 (버블차트용).
     * Object[] = { label, category, diaryCount(Long), avgIntensity(Double) }
     */
    List<Object[]> findMonthlyLabelStats(String username, int year, int month);

    /**
     * 특정 월에 특정 label이 기록된 Voice 목록 (일기 리스트용).
     */
    List<Voice> findVoicesByLabel(String username, String label, int year, int month);
}
