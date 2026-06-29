package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceComposite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoiceCompositeRepository extends JpaRepository<VoiceComposite, Long> {

    List<VoiceComposite> findByVoice_User_UsernameAndCreatedDateBetween(
            String username,
            LocalDateTime start,
            LocalDateTime end
    );

    List<VoiceComposite> findByVoice_IdIn(List<Long> voiceIds);

    Optional<VoiceComposite> findByVoice_Id(Long voiceId);

    void deleteByVoice_Id(Long voiceId);
}
