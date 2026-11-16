package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.organization.entity.OrganizationMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CareAssignmentRepository extends JpaRepository<CareAssignment, Long> {

    /**
     * 어르신의 현재 배정을 잠그며 읽는다. 정책상 한 건이지만, 어긋난 데이터도 재배정 때 함께 종료되도록 목록으로 받는다.
     *
     * <p>잠금 읽기라서 MySQL REPEATABLE READ에서도 스냅샷이 아니라 최신 커밋을 본다. 호출자 트랜잭션이 어르신 행을
     * 잠그기 전에 무언가를 읽었더라도, 먼저 끝난 동시 재배정이 보여 현재 담당자가 둘이 되지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CareAssignment a WHERE a.recipient = :recipient AND a.endedAt IS NULL")
    List<CareAssignment> findCurrentByRecipientForUpdate(@Param("recipient") CareRecipient recipient);

    /** 담당자의 현재 배정을 잠그며 읽는다(소속 종료 시 일괄 종료용). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CareAssignment a WHERE a.worker = :worker AND a.endedAt IS NULL")
    List<CareAssignment> findCurrentByWorkerForUpdate(@Param("worker") OrganizationMember worker);

    /** 대상자의 현재 배정(조회용, 잠금 없음). 담당자·계정을 함께 읽는다. */
    @Query("SELECT a FROM CareAssignment a JOIN FETCH a.worker w JOIN FETCH w.account "
            + "WHERE a.recipient = :recipient AND a.endedAt IS NULL")
    List<CareAssignment> findCurrentByRecipient(@Param("recipient") CareRecipient recipient);

    /** 담당자의 현재 배정(조회용, 잠금 없음). 대상자를 함께 읽는다. */
    @Query("SELECT a FROM CareAssignment a JOIN FETCH a.recipient WHERE a.worker = :worker AND a.endedAt IS NULL")
    List<CareAssignment> findCurrentByWorker(@Param("worker") OrganizationMember worker);

    boolean existsByRecipientAndWorkerAndEndedAtIsNull(CareRecipient recipient, OrganizationMember worker);
}
