package com.safori.domain.organization.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.model.StatusCount;
import com.safori.domain.organization.model.WorkerSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    boolean existsByOrganizationAndAccount(Organization organization, BackofficeAccount account);

    Optional<OrganizationMember> findByOrganizationAndAccount(Organization organization, BackofficeAccount account);

    boolean existsByAccountAndStatusNot(BackofficeAccount account, OrganizationMemberStatus status);

    /** 계정의 현재 멤버십(소속 종료 제외). 계정은 한 기관에만 소속되므로 최대 1건이다. 기관을 함께 읽는다. */
    @Query("""
            SELECT m
            FROM OrganizationMember m
            JOIN FETCH m.organization
            WHERE m.account = :account
              AND m.status <> com.safori.domain.organization.entity.OrganizationMemberStatus.REVOKED
            """)
    Optional<OrganizationMember> findCurrentByAccount(@Param("account") BackofficeAccount account);

    /**
     * 토큰이 가리키는 기관 멤버. 계정·기관을 함께 읽어 활성 상태 판정에서 추가 쿼리가 나가지 않게 한다.
     */
    @Query("""
            SELECT m
            FROM OrganizationMember m
            JOIN FETCH m.account a
            JOIN FETCH m.organization o
            WHERE a.accountUuid = :accountUuid
              AND o.publicId = :organizationPublicId
            """)
    Optional<OrganizationMember> findForAuthentication(@Param("accountUuid") String accountUuid,
                                                       @Param("organizationPublicId") String organizationPublicId);

    /** 권한 판정용 조회. 계정·기관을 함께 읽는다. */
    @Query("""
            SELECT m
            FROM OrganizationMember m
            JOIN FETCH m.account
            JOIN FETCH m.organization
            WHERE m.id = :memberId
            """)
    Optional<OrganizationMember> findForAuthorization(@Param("memberId") Long memberId);

    /**
     * 기관의 담당자(기본 그룹 CARE_WORKER 소속, 소속 종료 제외) 목록. 이름 부분 일치, 계정 상태로 거른다.
     * 정렬은 이름 → 구성원 id(같은 이름끼리도 순서를 고정해 페이지가 어긋나지 않게).
     */
    @Query(value = "SELECT new com.safori.domain.organization.model.WorkerSummary("
            + "a.accountUuid, a.name, m.jobTitle, a.status, "
            + "(SELECT COUNT(ca) FROM CareAssignment ca WHERE ca.worker = m AND ca.endedAt IS NULL)) "
            + WORKERS + "AND (:status IS NULL OR a.status = :status) ORDER BY a.name ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) " + WORKERS + "AND (:status IS NULL OR a.status = :status)")
    Page<WorkerSummary> findWorkers(@Param("organizationId") Long organizationId,
                                    @Param("keyword") String keyword,
                                    @Param("status") BackofficeAccountStatus status,
                                    Pageable pageable);

    /** 담당자 목록 탭 개수. 검색어는 목록과 같이 적용한다. */
    @Query("SELECT new com.safori.domain.organization.model.StatusCount(a.status, COUNT(m)) "
            + WORKERS + "GROUP BY a.status")
    List<StatusCount> countWorkersByStatus(@Param("organizationId") Long organizationId,
                                           @Param("keyword") String keyword);

    String WORKERS = """
            FROM OrganizationMember m
            JOIN m.account a
            WHERE m.organization.id = :organizationId
              AND m.status <> com.safori.domain.organization.entity.OrganizationMemberStatus.REVOKED
              AND EXISTS (SELECT 1 FROM AccessGroupMember gm
                          WHERE gm.member = m AND gm.group.systemCode = 'CARE_WORKER')
              AND (:keyword IS NULL OR a.name LIKE CONCAT('%', :keyword, '%'))
            """;
}
