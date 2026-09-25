package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupMember;
import com.safori.domain.access.policy.PermissionGrant;
import com.safori.domain.organization.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessGroupMemberRepository extends JpaRepository<AccessGroupMember, AccessGroupMember.Key> {

    boolean existsByGroupAndMember(AccessGroup group, OrganizationMember member);

    Optional<AccessGroupMember> findByGroupAndMember(AccessGroup group, OrganizationMember member);

    List<AccessGroupMember> findAllByMember(OrganizationMember member);

    /**
     * 구성원이 소속 그룹을 통해 상속받은 권한.
     *
     * <p>그룹·역할이 구성원과 같은 기관인지 쿼리에서 다시 확인한다. 서비스가 쓰기 시점에 막지만,
     * 잘못 들어간 교차 기관 행이 있어도 기관 A의 역할이 기관 B의 권한으로 합산되지 않게 하기 위해서다.
     */
    @Query("""
            SELECT new com.safori.domain.access.policy.PermissionGrant(p.code, r.dataScope)
            FROM AccessGroupMember gm
            JOIN gm.group g
            JOIN AccessGroupRole gr ON gr.group = g
            JOIN gr.role r
            JOIN AccessRolePermission rp ON rp.role = r
            JOIN rp.permission p
            WHERE gm.member.id = :memberId
              AND g.organization.id = :organizationId
              AND g.status = com.safori.domain.access.entity.AccessStatus.ACTIVE
              AND r.organization.id = :organizationId
              AND r.status = com.safori.domain.access.entity.AccessStatus.ACTIVE
            """)
    List<PermissionGrant> findGrantsInheritedFromGroups(@Param("memberId") Long memberId,
                                                        @Param("organizationId") Long organizationId);
}
