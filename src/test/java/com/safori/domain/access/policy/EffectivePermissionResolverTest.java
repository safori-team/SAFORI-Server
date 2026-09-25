package com.safori.domain.access.policy;

import com.safori.domain.access.adaptor.AccessGrantAdaptor;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.entity.OrganizationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.LINKED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 권한 = 그룹에서 상속한 역할의 권한 ∪ 개인에게 직접 부여한 역할의 권한.
 */
class EffectivePermissionResolverTest {

    private final FakeGrantAdaptor grants = new FakeGrantAdaptor();
    private final EffectivePermissionResolver resolver = new EffectivePermissionResolver(grants);

    @Test
    @DisplayName("그룹 상속분과 개인 역할분을 합치고, 같은 권한의 범위는 모은다")
    void mergesGroupAndDirectGrants() {
        grants.inherited = List.of(
                new PermissionGrant("RECIPIENT_READ", ASSIGNED_RECIPIENT),
                new PermissionGrant("CARE_REASON_READ", ASSIGNED_RECIPIENT));
        grants.direct = List.of(
                new PermissionGrant("RECIPIENT_READ", LINKED_RECIPIENT),
                new PermissionGrant("ASSIGNMENT_READ", ORGANIZATION));

        EffectivePermissions permissions = resolver.resolve(member(OrganizationMemberStatus.ACTIVE));

        assertThat(permissions.permissions()).containsExactlyInAnyOrder(RECIPIENT_READ, CARE_REASON_READ, ASSIGNMENT_READ);
        assertThat(permissions.scopesOf(RECIPIENT_READ)).containsExactlyInAnyOrder(ASSIGNED_RECIPIENT, LINKED_RECIPIENT);
        assertThat(permissions.scopesOf(ASSIGNMENT_READ)).containsExactly(ORGANIZATION);
        assertThat(permissions.hasScope(LINKED_RECIPIENT)).isTrue();
    }

    @Test
    @DisplayName("코드 카탈로그에 없는 권한 코드는 DB에 남아 있어도 무시한다")
    void unknownPermissionCodesAreIgnored() {
        grants.inherited = List.of(
                new PermissionGrant("RECIPIENT_READ", ASSIGNED_RECIPIENT),
                new PermissionGrant("REMOVED_PERMISSION", ORGANIZATION));

        EffectivePermissions permissions = resolver.resolve(member(OrganizationMemberStatus.ACTIVE));

        assertThat(permissions.permissions()).containsExactly(RECIPIENT_READ);
    }

    @Test
    @DisplayName("멤버십·계정·기관 중 하나라도 비활성이면 조회 없이 권한이 없다")
    void inactiveActorHasNoPermission() {
        grants.inherited = List.of(new PermissionGrant("RECIPIENT_READ", ORGANIZATION));

        OrganizationMember pending = member(OrganizationMemberStatus.PENDING);
        OrganizationMember suspendedAccount = member(OrganizationMemberStatus.ACTIVE);
        suspendedAccount.getAccount().suspend();
        OrganizationMember inactiveOrganization = member(OrganizationMemberStatus.ACTIVE);
        inactiveOrganization.getOrganization().deactivate();

        assertThat(resolver.resolve(pending).isEmpty()).isTrue();
        assertThat(resolver.resolve(suspendedAccount).isEmpty()).isTrue();
        assertThat(resolver.resolve(inactiveOrganization).isEmpty()).isTrue();
        assertThat(grants.calls).isZero();
    }

    @Test
    @DisplayName("개인 역할 조회에 판정 시각을 넘긴다 — 만료 여부는 이 시각으로 판단한다")
    void passesEvaluationTimeToDirectGrants() {
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 9, 24, 12, 0);

        resolver.resolve(member(OrganizationMemberStatus.ACTIVE), evaluatedAt);

        assertThat(grants.lastNow).isEqualTo(evaluatedAt);
    }

    private static OrganizationMember member(OrganizationMemberStatus status) {
        Organization organization = Organization.builder()
                .id(1L).publicId("organization-1").name("기관").status(OrganizationStatus.ACTIVE).build();
        BackofficeAccount account = BackofficeAccount.builder()
                .id(1L).accountUuid("account-1").loginId("login-1").passwordHash("hash").name("구성원")
                .status(BackofficeAccountStatus.ACTIVE).authVersion(0L).build();
        return OrganizationMember.builder()
                .id(1L).organization(organization).account(account).status(status).build();
    }

    private static class FakeGrantAdaptor implements AccessGrantAdaptor {

        List<PermissionGrant> inherited = List.of();
        List<PermissionGrant> direct = List.of();
        LocalDateTime lastNow;
        int calls;

        @Override
        public List<PermissionGrant> queryGrantsInheritedFromGroups(Long memberId, Long organizationId) {
            calls++;
            return inherited;
        }

        @Override
        public List<PermissionGrant> queryGrantsFromDirectRoles(Long memberId, Long organizationId, LocalDateTime now) {
            calls++;
            lastNow = now;
            return direct;
        }
    }
}
