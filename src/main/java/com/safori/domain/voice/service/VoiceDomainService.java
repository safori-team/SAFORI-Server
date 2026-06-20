package com.safori.domain.voice.service;

import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;

public interface VoiceDomainService {

    Voice uploadVoiceFile(User user, String voiceKey, String voiceTitle,
                          int duration, int sampleRate, int bitRate);
}
