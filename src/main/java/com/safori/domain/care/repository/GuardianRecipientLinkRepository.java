package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.organization.entity.OrganizationMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuardianRecipientLinkRepository extends JpaRepository<GuardianRecipientLink, Long> {

    /**
     * 어르신·보호자 쌍의 현재 연결을 잠그며 읽는다. 잠금 읽기라서 REPEATABLE READ에서도 먼저 커밋된
     * 동시 연결이 보여 같은 쌍이 두 번 연결되지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM GuardianRecipientLink l
            WHERE l.recipient = :recipient
              AND l.guardian = :guardian
              AND l.endedAt IS NULL
            """)
    List<GuardianRecipientLink> findCurrentByRecipientAndGuardianForUpdate(@Param("recipient") CareRecipient recipient,
                                                                         @Param("guardian") OrganizationMember guardian);

    /** 보호자의 현재 연결을 잠그며 읽는다(소속 종료 시 일괄 종료용). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM GuardianRecipientLink l WHERE l.guardian = :guardian AND l.endedAt IS NULL")
    List<GuardianRecipientLink> findCurrentByGuardianForUpdate(@Param("guardian") OrganizationMember guardian);

    /** 보호자의 현재 연결(보호자는 대상자 한 명에만 연결된다). */
    Optional<GuardianRecipientLink> findFirstByGuardianAndEndedAtIsNull(OrganizationMember guardian);

    /** 대상자의 현재 연결 보호자들(연결 순). */
    @Query("""
            SELECT l FROM GuardianRecipientLink l
            JOIN FETCH l.guardian g
            JOIN FETCH g.account
            WHERE l.recipient = :recipient AND l.endedAt IS NULL
            ORDER BY l.startedAt ASC, l.id ASC
            """)
    List<GuardianRecipientLink> findCurrentByRecipient(@Param("recipient") CareRecipient recipient);

    boolean existsByRecipientAndGuardianAndEndedAtIsNull(CareRecipient recipient, OrganizationMember guardian);
}
