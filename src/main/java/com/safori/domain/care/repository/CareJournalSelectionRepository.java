package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareJournalSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CareJournalSelectionRepository extends JpaRepository<CareJournalSelection, Long> {

    /** 여러 일지의 선택 항목을 한 번에(섹션·부모 포함). 목록에서 일지마다 조회하지 않기 위해서다. */
    @Query("SELECT s FROM CareJournalSelection s JOIN FETCH s.option o JOIN FETCH o.group LEFT JOIN FETCH o.parent "
            + "WHERE s.journal IN :journals ORDER BY o.sortOrder ASC")
    List<CareJournalSelection> findByJournals(@Param("journals") Collection<CareJournal> journals);

    /** {@link #findByJournals}와 같고 일지 id로 받는다(목록은 일지 엔티티 대신 id만 읽는다). */
    @Query("SELECT s FROM CareJournalSelection s JOIN FETCH s.option o JOIN FETCH o.group LEFT JOIN FETCH o.parent "
            + "WHERE s.journal.id IN :journalIds ORDER BY o.sortOrder ASC")
    List<CareJournalSelection> findByJournalIds(@Param("journalIds") Collection<Long> journalIds);
}
