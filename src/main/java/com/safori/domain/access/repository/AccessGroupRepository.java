package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessGroupRepository extends JpaRepository<AccessGroup, Long> {

    Optional<AccessGroup> findByOrganizationAndSystemCode(Organization organization, String systemCode);

    List<AccessGroup> findAllByOrganizationOrderByIdAsc(Organization organization);
}
