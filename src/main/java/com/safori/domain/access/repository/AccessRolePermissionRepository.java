package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessRolePermissionRepository
        extends JpaRepository<AccessRolePermission, AccessRolePermission.Key> {

    boolean existsByRoleAndPermission(AccessRole role, AccessPermission permission);

    Optional<AccessRolePermission> findByRoleAndPermission(AccessRole role, AccessPermission permission);

    @Query("""
            SELECT p.code
            FROM AccessRolePermission rp
            JOIN rp.permission p
            WHERE rp.role = :role
            """)
    List<String> findPermissionCodesByRole(@Param("role") AccessRole role);
}
