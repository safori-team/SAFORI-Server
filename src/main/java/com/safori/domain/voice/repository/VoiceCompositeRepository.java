package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceComposite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 같은 트랜잭션 안에서 삭제 → 재삽입할 때 쓰는 벌크 삭제. 파생 삭제는 INSERT가 먼저 플러시돼
     * {@code uq_vcp_voice}를 위반할 수 있다. 상세는 {@link VoiceEmotionLabelRepository#deleteAllByVoiceIdInBulk}.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from VoiceComposite x where x.voice.id = :voiceId")
    void deleteAllByVoiceIdInBulk(@Param("voiceId") Long voiceId);
}
