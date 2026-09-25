package com.safori.domain.organization.repository;

import com.safori.domain.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(String publicId);

    /** 기관 단위 규칙(관리자 1명 등)을 검사하고 쓰는 동안 같은 기관의 동시 변경을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :organizationId")
    Optional<Organization> findByIdForUpdate(@Param("organizationId") Long organizationId);
}
