package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessRoleRepository extends JpaRepository<AccessRole, Long> {

    Optional<AccessRole> findByOrganizationAndCode(Organization organization, String code);

    boolean existsByOrganizationAndCode(Organization organization, String code);

    List<AccessRole> findAllByOrganizationOrderByIdAsc(Organization organization);
}
