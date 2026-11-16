package com.safori.domain.care.repository;

import com.safori.domain.care.entity.JournalOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalOptionGroupRepository extends JpaRepository<JournalOptionGroup, String> {

    List<JournalOptionGroup> findAllByOrderBySortOrderAsc();
}
