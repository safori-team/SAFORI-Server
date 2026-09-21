package com.safori.domain.voice.adaptor;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.VoiceEmotionReport;

import java.util.Map;
import java.util.Optional;

public interface VoiceEmotionReportAdaptor {

    VoiceEmotionReport save(VoiceEmotionReport report);

    Optional<VoiceEmotionReport> findByVoiceIdAndUsername(Long voiceId, String username);

    /**
     * 여러 voice의 사용자 신고 감정을 한 번에 조회 (목록 조회 대표감정 오버라이드용).
     * @return voiceId → 신고 감정 (신고 없는 voice는 키 없음)
     */
    Map<Long, EmotionType> findReportedEmotions(java.util.List<Long> voiceIds, String username);
}
