package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessMemberRole;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.policy.PermissionGrant;
import com.safori.domain.organization.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccessMemberRoleRepository extends JpaRepository<AccessMemberRole, AccessMemberRole.Key> {

    Optional<AccessMemberRole> findByMemberAndRole(OrganizationMember member, AccessRole role);

    /**
     * 구성원에게 직접 부여된 역할의 권한. 회수됐거나 {@code now} 시점에 만료된 부여는 뺀다.
     * 역할이 구성원과 같은 기관인지 쿼리에서 다시 확인한다.
     */
    @Query("""
            SELECT new com.safori.domain.access.policy.PermissionGrant(p.code, r.dataScope)
            FROM AccessMemberRole mr
            JOIN mr.role r
            JOIN AccessRolePermission rp ON rp.role = r
            JOIN rp.permission p
            WHERE mr.member.id = :memberId
              AND mr.revokedAt IS NULL
              AND (mr.expiresAt IS NULL OR mr.expiresAt > :now)
              AND r.organization.id = :organizationId
              AND r.status = com.safori.domain.access.entity.AccessStatus.ACTIVE
            """)
    List<PermissionGrant> findGrantsFromDirectRoles(@Param("memberId") Long memberId,
                                                    @Param("organizationId") Long organizationId,
                                                    @Param("now") LocalDateTime now);
}
