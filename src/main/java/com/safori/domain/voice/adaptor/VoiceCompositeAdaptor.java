package com.safori.domain.voice.adaptor;

import com.safori.domain.voice.entity.VoiceComposite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoiceCompositeAdaptor {

    List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end);
    List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds);
    Optional<VoiceComposite> findByVoiceId(Long voiceId);
    VoiceComposite save(VoiceComposite voiceComposite);
    void deleteByVoiceId(Long voiceId);
}
