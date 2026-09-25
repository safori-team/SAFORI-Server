package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CareRecordRepository extends JpaRepository<CareRecord, Long> {

    Optional<CareRecord> findByRecipientAndPublicId(CareRecipient recipient, String publicId);

    /** 이 사유를 마지막으로 완료한 시각. 없으면 null. */
    @Query("""
            SELECT MAX(r.processedAt) FROM CareRecord r
            WHERE r.recipient = :recipient AND r.reasonType = :reasonType
              AND r.processingStatus = com.safori.domain.care.entity.CareProcessingStatus.DONE
            """)
    LocalDateTime findLastCompletedAt(@Param("recipient") CareRecipient recipient,
                                      @Param("reasonType") CareReasonType reasonType);
}
