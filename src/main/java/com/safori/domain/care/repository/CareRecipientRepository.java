package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareRecipient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CareRecipientRepository extends JpaRepository<CareRecipient, Long> {

    /**
     * 역할 범위별 조회 가능 어르신 조건. 기관 전체 / 현재 본인 배정 / 현재 본인 연결 중 하나라도 맞으면 포함한다.
     * EXISTS로 거르기 때문에 여러 역할·관계가 겹쳐도 행이 중복되지 않아 페이지 count가 틀어지지 않는다.
     */
    String ACCESSIBLE_RECIPIENTS = """
            FROM CareRecipient r
            WHERE r.organization.id = :organizationId
              AND r.status = com.safori.domain.care.entity.CareRecipientStatus.ACTIVE
              AND (
                    :organizationWide = TRUE
                 OR (:includesAssigned = TRUE AND EXISTS (
                        SELECT 1 FROM CareAssignment a
                        WHERE a.recipient = r
                          AND a.worker.id = :memberId
                          AND a.endedAt IS NULL))
                 OR (:includesLinked = TRUE AND EXISTS (
                        SELECT 1 FROM GuardianRecipientLink l
                        WHERE l.recipient = r
                          AND l.guardian.id = :memberId
                          AND l.endedAt IS NULL))
              )
            """;

    Optional<CareRecipient> findByPublicId(String publicId);

    boolean existsByUserId(Long userId);

    /** 배정·연결 변경을 직렬화하기 위한 어르신 행 잠금. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CareRecipient r WHERE r.id = :recipientId")
    Optional<CareRecipient> findByIdForUpdate(@Param("recipientId") Long recipientId);

    @Query(value = "SELECT r " + ACCESSIBLE_RECIPIENTS,
            countQuery = "SELECT COUNT(r) " + ACCESSIBLE_RECIPIENTS)
    Page<CareRecipient> findAccessible(@Param("organizationId") Long organizationId,
                                       @Param("memberId") Long memberId,
                                       @Param("organizationWide") boolean organizationWide,
                                       @Param("includesAssigned") boolean includesAssigned,
                                       @Param("includesLinked") boolean includesLinked,
                                       Pageable pageable);
}
