package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecipientStatus;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.care.model.RecipientStatusCounts;
import com.safori.domain.care.model.RecipientStatusRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /** 어르신 앱 계정의 대상자(어르신은 한 기관에만 등록된다). */
    Optional<CareRecipient> findByUserIdAndStatus(Long userId, CareRecipientStatus status);

    /** 판정 배치 대상: 앱 계정과 연결된 활성 대상자. */
    List<CareRecipient> findAllByStatusAndUserIdIsNotNull(CareRecipientStatus status);

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

    /**
     * 대상자 현황·목록. 조회 범위(기관 전체 / 본인 배정 / 본인 연결)는 {@link #findAccessible}과 같다.
     * 정렬: 상태 코드 높은 순 → 감지 시각 최신순 → 대상자 id(같은 값끼리 순서 고정).
     *
     * @param assigned null이면 배정 여부로 거르지 않는다
     */
    @Query(value = "SELECT new com.safori.domain.care.model.RecipientStatusRow("
            + "r.publicId, u.name, u.birthDate, rec.publicId, rec.statusCode, rec.reasonMessage, "
            + "rec.processingStatus, rec.detectedAt, wa.accountUuid, wa.name) "
            + STATUS_BOARD + STATUS_FILTERS
            + "ORDER BY CASE rec.statusCode "
            + "WHEN com.safori.domain.care.entity.CareStatusCode.URGENT THEN 3 "
            + "WHEN com.safori.domain.care.entity.CareStatusCode.CAUTION THEN 2 "
            + "WHEN com.safori.domain.care.entity.CareStatusCode.INTEREST THEN 1 ELSE 0 END DESC, "
            + "rec.detectedAt DESC, r.id DESC",
            countQuery = "SELECT COUNT(r) " + STATUS_BOARD + STATUS_FILTERS)
    Page<RecipientStatusRow> findStatusBoard(@Param("organizationId") Long organizationId,
                                             @Param("memberId") Long memberId,
                                             @Param("organizationWide") boolean organizationWide,
                                             @Param("includesAssigned") boolean includesAssigned,
                                             @Param("includesLinked") boolean includesLinked,
                                             @Param("keyword") String keyword,
                                             @Param("managerId") String managerId,
                                             @Param("statusCode") CareStatusCode statusCode,
                                             @Param("assigned") Boolean assigned,
                                             Pageable pageable);

    /** {@link #findStatusBoard}의 탭 개수(상태 코드·배정 탭 필터 제외). */
    @Query("SELECT new com.safori.domain.care.model.RecipientStatusCounts(COUNT(r), "
            + "SUM(CASE WHEN ca.id IS NOT NULL THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN rec.statusCode = com.safori.domain.care.entity.CareStatusCode.URGENT THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN rec.statusCode = com.safori.domain.care.entity.CareStatusCode.CAUTION THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN rec.statusCode = com.safori.domain.care.entity.CareStatusCode.INTEREST THEN 1 ELSE 0 END)) "
            + STATUS_BOARD)
    RecipientStatusCounts countStatusBoard(@Param("organizationId") Long organizationId,
                                           @Param("memberId") Long memberId,
                                           @Param("organizationWide") boolean organizationWide,
                                           @Param("includesAssigned") boolean includesAssigned,
                                           @Param("includesLinked") boolean includesLinked,
                                           @Param("keyword") String keyword,
                                           @Param("managerId") String managerId);

    /** 대상자 + 현재 기록 + 어르신 계정(이름·생년월일) + 현재 담당자. 범위·검색어·담당자 필터까지. */
    String STATUS_BOARD = """
            FROM CareRecipient r
            LEFT JOIN r.currentRecord rec
            LEFT JOIN User u ON u.id = r.userId
            LEFT JOIN CareAssignment ca ON ca.recipient = r AND ca.endedAt IS NULL
            LEFT JOIN ca.worker w
            LEFT JOIN w.account wa
            WHERE r.organization.id = :organizationId
              AND r.status = com.safori.domain.care.entity.CareRecipientStatus.ACTIVE
              AND (
                    :organizationWide = TRUE
                 OR (:includesAssigned = TRUE AND w.id = :memberId)
                 OR (:includesLinked = TRUE AND EXISTS (
                        SELECT 1 FROM GuardianRecipientLink l
                        WHERE l.recipient = r AND l.guardian.id = :memberId AND l.endedAt IS NULL))
              )
              AND (:managerId IS NULL OR wa.accountUuid = :managerId)
              AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') OR wa.name LIKE CONCAT('%', :keyword, '%'))
            """;

    String STATUS_FILTERS = """
              AND (:statusCode IS NULL OR rec.statusCode = :statusCode)
              AND (:assigned IS NULL OR (:assigned = TRUE AND ca.id IS NOT NULL) OR (:assigned = FALSE AND ca.id IS NULL))
            """;
}
