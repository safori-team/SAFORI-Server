package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessRoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessRoleTemplateRepository extends JpaRepository<AccessRoleTemplate, Long> {

    Optional<AccessRoleTemplate> findByCodeAndVersion(String code, long version);
}
