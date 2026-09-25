package com.safori.domain.access.repository;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupMember;
import com.safori.domain.access.entity.AccessGroupRole;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.entity.DataScope;
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

import java.util.List;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 그룹 상속 권한 쿼리. 최종 권한 계산의 절반이라 조건 하나가 빠지면 권한이 새거나 사라진다.
 */
@DataJpaTest
class AccessGroupMemberRepositoryTest {

    @Autowired AccessGroupMemberRepository repository;
    @Autowired EntityManager em;

    private Organization organization;
    private OrganizationMember member;
    private AccessPermission recipientRead;
    private AccessPermission careReasonRead;

    @BeforeEach
    void setUp() {
        organization = persist(Organization.create("기관"));
        member = persistMember(organization);
        recipientRead = persist(AccessPermission.from(RECIPIENT_READ));
        careReasonRead = persist(AccessPermission.from(CARE_REASON_READ));
    }

    @Test
    @DisplayName("소속 그룹에 연결된 역할의 권한을 그 역할의 데이터 범위와 함께 돌려준다")
    void returnsPermissionsOfGroupRoles() {
        AccessRole workerRole = persistRole(organization, "CARE_WORKER", ASSIGNED_RECIPIENT, recipientRead, careReasonRead);
        AccessGroup group = persistGroupWithRole(organization, workerRole);
        persist(AccessGroupMember.of(group, member));

        assertThat(grants()).containsExactlyInAnyOrder(
                new PermissionGrant("RECIPIENT_READ", ASSIGNED_RECIPIENT),
                new PermissionGrant("CARE_REASON_READ", ASSIGNED_RECIPIENT));
    }

    @Test
    @DisplayName("소속되지 않은 그룹의 역할은 돌려주지 않는다")
    void ignoresGroupsTheMemberIsNotIn() {
        persistGroupWithRole(organization, persistRole(organization, "ORG_ADMIN", ORGANIZATION, recipientRead));

        assertThat(grants()).isEmpty();
    }

    @Test
    @DisplayName("비활성 그룹이나 비활성 역할의 권한은 빠진다")
    void ignoresInactiveGroupOrRole() {
        AccessRole role = persistRole(organization, "CARE_WORKER", ASSIGNED_RECIPIENT, recipientRead);
        AccessGroup group = persistGroupWithRole(organization, role);
        persist(AccessGroupMember.of(group, member));

        group.deactivate();
        assertThat(grants()).as("그룹 비활성").isEmpty();

        group.activate();
        role.deactivate();
        assertThat(grants()).as("역할 비활성").isEmpty();
    }

    @Test
    @DisplayName("그룹에 다른 기관 역할이 잘못 연결돼 있어도 권한으로 돌려주지 않는다")
    void ignoresRolesOfOtherOrganizations() {
        Organization foreignOrganization = persist(Organization.create("다른 기관"));
        AccessRole foreignRole = persistRole(foreignOrganization, "ORG_ADMIN", ORGANIZATION, recipientRead);
        // AccessGroupDomainService#assignRole이 막는 교차 기관 연결을 저장소로 직접 넣는다
        AccessGroup group = persistGroupWithRole(organization, foreignRole);
        persist(AccessGroupMember.of(group, member));

        assertThat(grants()).isEmpty();
    }

    private List<PermissionGrant> grants() {
        return repository.findGrantsInheritedFromGroups(member.getId(), organization.getId());
    }

    private OrganizationMember persistMember(Organization organization) {
        BackofficeAccount account = persist(BackofficeAccount.create("worker", "hash", "담당자"));
        return persist(OrganizationMember.invite(organization, account, null));
    }

    private AccessRole persistRole(Organization organization, String code, DataScope scope,
                                   AccessPermission... permissions) {
        AccessRole role = persist(AccessRole.custom(organization, code, code, scope));
        for (AccessPermission permission : permissions) {
            persist(AccessRolePermission.of(role, permission));
        }
        return role;
    }

    private AccessGroup persistGroupWithRole(Organization organization, AccessRole role) {
        AccessGroup group = persist(AccessGroup.custom(organization, role.getCode() + " 그룹"));
        persist(AccessGroupRole.of(group, role));
        return group;
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
