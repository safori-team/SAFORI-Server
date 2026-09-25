package com.safori.domain.care.repository;

import com.safori.domain.care.entity.JournalOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JournalOptionRepository extends JpaRepository<JournalOption, String> {

    /** 폼에 보일 항목(활성). 섹션·부모를 함께 읽는다. */
    @Query("SELECT o FROM JournalOption o JOIN FETCH o.group LEFT JOIN FETCH o.parent "
            + "WHERE o.active = TRUE ORDER BY o.sortOrder ASC")
    List<JournalOption> findActiveWithGroup();
}
