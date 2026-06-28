package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceEmotionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoiceEmotionReportRepository extends JpaRepository<VoiceEmotionReport, Long> {

    Optional<VoiceEmotionReport> findByVoice_IdAndUser_Username(Long voiceId, String username);

    List<VoiceEmotionReport> findByVoice_IdInAndUser_Username(List<Long> voiceIds, String username);
}
