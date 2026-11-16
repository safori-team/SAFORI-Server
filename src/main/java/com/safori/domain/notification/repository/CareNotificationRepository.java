package com.safori.domain.notification.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.notification.entity.CareNotification;
import com.safori.domain.notification.entity.CareNotificationType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareNotificationRepository extends JpaRepository<CareNotification, Long> {

    boolean existsByRecordAndTypeAndAccount(CareRecord record, CareNotificationType type, BackofficeAccount account);

    /**
     * 48시간 미확인 알림을 보낼 기록: 활성 대상자의 현재 기록이 주의·미확인이고 {@code before} 이전에 감지됐으며
     * 아직 이 알림을 보낸 적이 없다. 묻힌(ABSORBED)·완료된 기록은 현재 기록이 아니라 빠진다.
     */
    @Query("""
            SELECT r.id FROM CareRecipient cr JOIN cr.currentRecord r
            WHERE cr.status = com.safori.domain.care.entity.CareRecipientStatus.ACTIVE
              AND r.statusCode = com.safori.domain.care.entity.CareStatusCode.CAUTION
              AND r.processingStatus = com.safori.domain.care.entity.CareProcessingStatus.UNCHECKED
              AND r.detectedAt <= :before
              AND NOT EXISTS (SELECT 1 FROM CareNotification n
                              WHERE n.record = r
                                AND n.type = com.safori.domain.notification.entity.CareNotificationType.CAUTION_UNCHECKED_48H)
            """)
    List<Long> findCautionUncheckedRecordIds(@Param("before") LocalDateTime before);
}
