package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.VoiceContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoiceContentRepository extends JpaRepository<VoiceContent, Long> {
    Optional<VoiceContent> findByVoice_Id(Long voiceId);

    List<VoiceContent> findByVoice_IdIn(List<Long> voiceIds);

    /**
     * 같은 트랜잭션 안에서 삭제 → 재삽입할 때 쓰는 벌크 삭제. 파생 삭제는 INSERT가 먼저 플러시돼
     * {@code uq_vc_voice}를 위반할 수 있다. 상세는 {@link VoiceEmotionLabelRepository#deleteAllByVoiceIdInBulk}.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from VoiceContent x where x.voice.id = :voiceId")
    void deleteAllByVoiceIdInBulk(@Param("voiceId") Long voiceId);
}
