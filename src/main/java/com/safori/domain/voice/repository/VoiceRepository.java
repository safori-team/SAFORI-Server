package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceRepository extends JpaRepository<Voice, Long> {
    List<Voice> findByUser_Username(String username);

    List<Voice> findByUser_UsernameAndCreatedDateBetween(String username,
                                                         LocalDateTime start,
                                                         LocalDateTime end);

    /** 최근 N건 (홈화면 미리보기용) */
    List<Voice> findByUser_UsernameOrderByCreatedDateDesc(String username, Pageable pageable);
}
