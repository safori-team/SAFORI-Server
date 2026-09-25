package com.safori.domain.care.repository;

import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.model.JournalListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CareJournalRepository extends JpaRepository<CareJournal, Long> {

    @EntityGraph(attributePaths = {"writer", "writer.account"})
    Optional<CareJournal> findByRecipientAndPublicId(CareRecipient recipient, String publicId);

    /** 최근 조치 기록: 확인 일시 최신순(같으면 작성 순서 역순). */
    @EntityGraph(attributePaths = {"writer", "writer.account"})
    List<CareJournal> findByRecipientOrderByConfirmedAtDescIdDesc(CareRecipient recipient, Pageable pageable);

    /**
     * 일지 목록. 조회 범위(기관 전체 / 현재 본인 배정 / 현재 본인 연결)는 대상자 현황과 같고, 연결 범위(보호자)로만
     * 보는 일지는 보호자 공개 일지뿐이다. 확인 일시 {@code [from, to)} 안에서 최신순(같으면 id 역순).
     *
     * @param keyword   대상자 이름 또는 작성자 이름 (부분 일치). null이면 거르지 않는다
     * @param managerId 작성자(계정 UUID). null이면 거르지 않는다
     */
    @Query(value = "SELECT new com.safori.domain.care.model.JournalListRow("
            + "j.id, j.publicId, r.publicId, u.name, j.confirmedAt, j.statusCodeSnapshot, wa.name) "
            + JOURNALS + "ORDER BY j.confirmedAt DESC, j.id DESC",
            countQuery = "SELECT COUNT(j) " + JOURNALS)
    Page<JournalListRow> findJournals(@Param("organizationId") Long organizationId,
                                      @Param("memberId") Long memberId,
                                      @Param("organizationWide") boolean organizationWide,
                                      @Param("includesAssigned") boolean includesAssigned,
                                      @Param("includesLinked") boolean includesLinked,
                                      @Param("keyword") String keyword,
                                      @Param("managerId") String managerId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);

    String JOURNALS = """
            FROM CareJournal j
            JOIN j.recipient r
            JOIN j.writer w
            JOIN w.account wa
            LEFT JOIN User u ON u.id = r.userId
            WHERE r.organization.id = :organizationId
              AND r.status = com.safori.domain.care.entity.CareRecipientStatus.ACTIVE
              AND (
                    :organizationWide = TRUE
                 OR (:includesAssigned = TRUE AND EXISTS (
                        SELECT 1 FROM CareAssignment a
                        WHERE a.recipient = r AND a.worker.id = :memberId AND a.endedAt IS NULL))
                 OR (:includesLinked = TRUE AND j.guardianVisible = TRUE AND EXISTS (
                        SELECT 1 FROM GuardianRecipientLink l
                        WHERE l.recipient = r AND l.guardian.id = :memberId AND l.endedAt IS NULL))
              )
              AND j.confirmedAt >= :from AND j.confirmedAt < :to
              AND (:managerId IS NULL OR wa.accountUuid = :managerId)
              AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') OR wa.name LIKE CONCAT('%', :keyword, '%'))
            """;
}
