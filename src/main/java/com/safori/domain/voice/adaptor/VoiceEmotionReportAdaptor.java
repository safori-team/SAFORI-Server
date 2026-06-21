package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.VoiceEmotionReport;

import java.util.Optional;

public interface VoiceEmotionReportAdaptor {

    VoiceEmotionReport save(VoiceEmotionReport report);

    Optional<VoiceEmotionReport> findByVoiceIdAndUsername(Long voiceId, String username);
}
