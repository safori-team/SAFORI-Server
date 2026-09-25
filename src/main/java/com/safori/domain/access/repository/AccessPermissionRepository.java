package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessPermissionRepository extends JpaRepository<AccessPermission, Long> {

    Optional<AccessPermission> findByCode(String code);
}
