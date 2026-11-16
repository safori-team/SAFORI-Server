package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupRole;
import com.safori.domain.access.entity.AccessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessGroupRoleRepository extends JpaRepository<AccessGroupRole, AccessGroupRole.Key> {

    boolean existsByGroupAndRole(AccessGroup group, AccessRole role);

    Optional<AccessGroupRole> findByGroupAndRole(AccessGroup group, AccessRole role);

    List<AccessGroupRole> findAllByGroup(AccessGroup group);
}
