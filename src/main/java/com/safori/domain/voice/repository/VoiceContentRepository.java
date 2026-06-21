package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoiceContentRepository extends JpaRepository<VoiceContent, Long> {
    Optional<VoiceContent> findByVoice_Id(Long voiceId);

    List<VoiceContent> findByVoice_IdIn(List<Long> voiceIds);
}
