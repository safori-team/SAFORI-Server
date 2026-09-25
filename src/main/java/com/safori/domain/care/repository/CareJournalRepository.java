package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareRecipient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareJournalRepository extends JpaRepository<CareJournal, Long> {

    @EntityGraph(attributePaths = {"writer", "writer.account"})
    Optional<CareJournal> findByRecipientAndPublicId(CareRecipient recipient, String publicId);

    /** 최근 조치 기록: 확인 일시 최신순(같으면 작성 순서 역순). */
    @EntityGraph(attributePaths = {"writer", "writer.account"})
    List<CareJournal> findByRecipientOrderByConfirmedAtDescIdDesc(CareRecipient recipient, Pageable pageable);
}
