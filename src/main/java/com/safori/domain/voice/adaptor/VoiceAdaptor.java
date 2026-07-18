package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.Voice;

import java.time.LocalDate;
import java.util.List;

public interface VoiceAdaptor {

    Voice queryById(Long voiceId);

    List<Voice> queryByUsername(String username);

    List<Voice> queryByUsernameAndCreatedAt(String username, LocalDate createdAt);

    List<Voice> queryLatestByUsername(String username, int limit);

    Voice save(Voice voice);

    void deleteById(Long voiceId);

    /** 작성일 강제 갱신 (개발용 시딩 전용). @CreatedDate 감사를 우회한다. */
    void backdateCreatedDate(Long voiceId, java.time.LocalDateTime createdDate);
}
