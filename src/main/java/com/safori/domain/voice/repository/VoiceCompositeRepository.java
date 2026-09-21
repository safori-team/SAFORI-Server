package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceComposite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoiceCompositeRepository extends JpaRepository<VoiceComposite, Long> {

    List<VoiceComposite> findByVoice_User_UsernameAndCreatedDateBetween(
            String username,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * 기간 내 마음일기(voice_composite)를 하나라도 가진 사용자 ID 목록(중복 제거).
     * 주간 리포트 푸시 대상 선정에 쓰인다. end 는 exclusive.
     */
    @Query("select distinct vc.voice.user.id from VoiceComposite vc "
            + "where vc.createdDate >= :start and vc.createdDate < :end")
    List<Long> findDistinctUserIdsByCreatedDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<VoiceComposite> findByVoice_IdIn(List<Long> voiceIds);

    Optional<VoiceComposite> findByVoice_Id(Long voiceId);

    void deleteByVoice_Id(Long voiceId);
}
