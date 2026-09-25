package com.safori.domain.organization.repository;

import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(String publicId);

    /** 운영자 기관 목록. 기관명 부분 일치·상태로 거르고, 최근 생성순(같으면 id 역순). */
    @Query("""
            SELECT o FROM Organization o
            WHERE (:keyword IS NULL OR o.name LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR o.status = :status)
            ORDER BY o.createdDate DESC, o.id DESC
            """)
    Page<Organization> search(@Param("keyword") String keyword,
                              @Param("status") OrganizationStatus status,
                              Pageable pageable);

    /** 기관 단위 규칙(관리자 1명 등)을 검사하고 쓰는 동안 같은 기관의 동시 변경을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :organizationId")
    Optional<Organization> findByIdForUpdate(@Param("organizationId") Long organizationId);
}
