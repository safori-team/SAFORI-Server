package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceRepository extends JpaRepository<Voice, Long> {
}
