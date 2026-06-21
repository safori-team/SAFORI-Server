package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.Voice;

import java.time.LocalDate;
import java.util.List;

public interface VoiceAdaptor {

    Voice queryById(Long voiceId);

    List<Voice> queryByUsername(String username);

    List<Voice> queryByUsernameAndCreatedAt(String username, LocalDate createdAt);

    void deleteById(Long voiceId);
}
