package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.VoiceContent;

public interface VoiceContentAdaptor {
    VoiceContent save(VoiceContent voiceContent);
    void deleteByVoiceId(Long voiceId);
}
