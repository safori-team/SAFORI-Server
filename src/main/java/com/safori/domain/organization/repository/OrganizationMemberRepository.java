package com.safori.domain.organization.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.model.GuardianCounts;
import com.safori.domain.organization.model.GuardianSummary;
import com.safori.domain.organization.model.OrganizationCount;
import com.safori.domain.organization.model.StatusCount;
import com.safori.domain.organization.model.WorkerSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * 기관의 보호자(기본 그룹 GUARDIAN 소속, 소속 종료 제외)와 현재 연결 대상자. 보호자는 대상자 한 명에만 연결된다.
     * 검색어는 보호자 이름 또는 연결 대상자 이름. 정렬은 이름 → 구성원 id.
     *
     * @param linked null이면 연결 여부로 거르지 않는다
     */
    @Query(value = "SELECT new com.safori.domain.organization.model.GuardianSummary("
            + "a.accountUuid, a.name, a.status, r.publicId, u.name, l.relation, l.relationText) "
            + GUARDIANS + LINKED_FILTER + "ORDER BY a.name ASC, m.id ASC",
            countQuery = "SELECT COUNT(m) " + GUARDIANS + LINKED_FILTER)
    Page<GuardianSummary> findGuardians(@Param("organizationId") Long organizationId,
                                        @Param("keyword") String keyword,
                                        @Param("linked") Boolean linked,
                                        Pageable pageable);

    /** 보호자 목록 탭 개수(전체·연결). 검색어는 목록과 같이 적용한다. */
    @Query("SELECT new com.safori.domain.organization.model.GuardianCounts(COUNT(m), "
            + "SUM(CASE WHEN l.id IS NOT NULL THEN 1 ELSE 0 END)) " + GUARDIANS)
    GuardianCounts countGuardians(@Param("organizationId") Long organizationId,
                                  @Param("keyword") String keyword);

    /**
     * 기관들의 현재 구성원(소속 종료 제외) 중 기본 그룹이 {@code templateCode}인 구성원. 계정을 함께 읽는다.
     * 운영자 기관 목록·상세의 관리자 표시용이다(관리자는 기관당 1명).
     */
    @Query("""
            SELECT m FROM OrganizationMember m
            JOIN FETCH m.account
            WHERE m.organization.id IN :organizationIds
              AND m.status <> com.safori.domain.organization.entity.OrganizationMemberStatus.REVOKED
              AND EXISTS (SELECT 1 FROM AccessGroupMember gm
                          WHERE gm.member = m AND gm.group.systemCode = :templateCode)
            """)
    List<OrganizationMember> findCurrentByTemplate(@Param("organizationIds") Collection<Long> organizationIds,
                                                   @Param("templateCode") String templateCode);

    /** 기관별 현재 구성원 수(기본 그룹 {@code templateCode}, 소속 종료 제외). 구성원이 없는 기관은 행이 없다. */
    @Query("""
            SELECT new com.safori.domain.organization.model.OrganizationCount(m.organization.id, COUNT(m))
            FROM OrganizationMember m
            WHERE m.organization.id IN :organizationIds
              AND m.status <> com.safori.domain.organization.entity.OrganizationMemberStatus.REVOKED
              AND EXISTS (SELECT 1 FROM AccessGroupMember gm
                          WHERE gm.member = m AND gm.group.systemCode = :templateCode)
            GROUP BY m.organization.id
            """)
    List<OrganizationCount> countCurrentByTemplate(@Param("organizationIds") Collection<Long> organizationIds,
                                                   @Param("templateCode") String templateCode);

    String GUARDIANS = """
            FROM OrganizationMember m
            JOIN m.account a
            LEFT JOIN GuardianRecipientLink l ON l.guardian = m AND l.endedAt IS NULL
            LEFT JOIN l.recipient r
            LEFT JOIN User u ON u.id = r.userId
            WHERE m.organization.id = :organizationId
              AND m.status <> com.safori.domain.organization.entity.OrganizationMemberStatus.REVOKED
              AND EXISTS (SELECT 1 FROM AccessGroupMember gm
                          WHERE gm.member = m AND gm.group.systemCode = 'GUARDIAN')
              AND (:keyword IS NULL OR a.name LIKE CONCAT('%', :keyword, '%') OR u.name LIKE CONCAT('%', :keyword, '%'))
            """;

    String LINKED_FILTER = """
              AND (:linked IS NULL OR (:linked = TRUE AND l.id IS NOT NULL) OR (:linked = FALSE AND l.id IS NULL))
            """;

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
