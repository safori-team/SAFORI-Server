package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CareRecordRepository extends JpaRepository<CareRecord, Long> {

    Optional<CareRecord> findByRecipientAndPublicId(CareRecipient recipient, String publicId);
}
