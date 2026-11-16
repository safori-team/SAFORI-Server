package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {

    Optional<AccessRole> findByOrganizationAndCode(Organization organization, String code);

    boolean existsByOrganizationAndCode(Organization organization, String code);

    List<AccessRole> findAllByOrganizationOrderByIdAsc(Organization organization);

    /** 해당 템플릿의 주어진 버전보다 낮은 버전으로 만들어진 역할. */
    @Query("""
            SELECT r
            FROM AccessRole r
            JOIN FETCH r.sourceTemplate t
            WHERE t.code = :code AND t.version < :version
            """)
    List<AccessRole> findAllBySourceTemplateOlderThan(@Param("code") String code, @Param("version") long version);
}
