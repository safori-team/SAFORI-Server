package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessMemberRole;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.policy.PermissionGrant;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개인 예외 역할 권한 쿼리. 회수·만료·기관 경계가 쿼리 조건에서 걸러지는지 본다.
 */
@DataJpaTest
class AccessMemberRoleRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Autowired AccessMemberRoleRepository repository;
    @Autowired EntityManager em;

    private Organization organization;
    private OrganizationMember member;
    private AccessPermission assignmentRead;
    private AccessRole viewerRole;

    @BeforeEach
    void setUp() {
        organization = persist(Organization.create("기관"));
        member = persistMember(organization);
        assignmentRead = persist(AccessPermission.from(ASSIGNMENT_READ));
        viewerRole = persistViewerRole(organization);
    }

    @Test
    @DisplayName("회수되지 않았고 만료 전인 개인 역할의 권한을 돌려준다")
    void returnsEffectiveDirectRoles() {
        persist(AccessMemberRole.grant(member, viewerRole, null, NOW.plusDays(1), "인수인계 점검", NOW.minusDays(1)));

        assertThat(grantsAt(NOW)).containsExactly(new PermissionGrant("ASSIGNMENT_READ", ORGANIZATION));
    }

    @Test
    @DisplayName("만료 시각이 되면 빠진다 — 만료 시각 정각부터 무효")
    void excludesExpiredRoles() {
        persist(AccessMemberRole.grant(member, viewerRole, null, NOW, null, NOW.minusDays(1)));

        assertThat(grantsAt(NOW.minusSeconds(1))).hasSize(1);
        assertThat(grantsAt(NOW)).isEmpty();
    }

    @Test
    @DisplayName("회수된 개인 역할은 빠진다")
    void excludesRevokedRoles() {
        AccessMemberRole grant = persist(AccessMemberRole.grant(member, viewerRole, null, null, null, NOW.minusDays(1)));

        grant.revoke(NOW.minusHours(1));

        assertThat(grantsAt(NOW)).isEmpty();
    }

    @Test
    @DisplayName("다른 기관 역할이나 비활성 역할이 부여돼 있으면 권한으로 돌려주지 않는다")
    void excludesForeignOrInactiveRoles() {
        AccessRole foreignRole = persistViewerRole(persist(Organization.create("다른 기관")));
        // AccessRoleDomainService#grantMemberRole이 막는 교차 기관 부여를 저장소로 직접 넣는다
        persist(AccessMemberRole.grant(member, foreignRole, null, null, "잘못된 데이터", NOW.minusDays(1)));
        assertThat(grantsAt(NOW)).as("다른 기관 역할").isEmpty();

        persist(AccessMemberRole.grant(member, viewerRole, null, null, null, NOW.minusDays(1)));
        viewerRole.deactivate();
        assertThat(grantsAt(NOW)).as("비활성 역할").isEmpty();
    }

    private List<PermissionGrant> grantsAt(LocalDateTime now) {
        return repository.findGrantsFromDirectRoles(member.getId(), organization.getId(), now);
    }

    private OrganizationMember persistMember(Organization organization) {
        BackofficeAccount account = persist(BackofficeAccount.create("worker", "hash", "담당자", null));
        return persist(OrganizationMember.invite(organization, account, null));
    }

    private AccessRole persistViewerRole(Organization organization) {
        AccessRole role = persist(AccessRole.custom(organization, "ASSIGNMENT_VIEWER", "배정 현황 조회", ORGANIZATION));
        persist(AccessRolePermission.of(role, assignmentRead));
        return role;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
