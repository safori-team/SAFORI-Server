package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.Voice;

public interface VoiceAdaptor {

    Voice queryById(Long voiceId);

    void deleteById(Long voiceId);
}
