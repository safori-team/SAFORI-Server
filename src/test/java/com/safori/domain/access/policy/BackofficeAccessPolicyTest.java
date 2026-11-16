package com.safori.domain.access.policy;

import com.safori.domain.access.PhotoPermissionMatrix;
import com.safori.domain.access.adaptor.AccessGrantAdaptor;
import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.care.adaptor.CareRecipientAdaptor;
import com.safori.domain.care.adaptor.CareRelationAdaptor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecipientStatus;
import com.safori.domain.organization.adaptor.OrganizationMemberAdaptor;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.entity.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.LINKED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_CREATE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_WRITE;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.GUARDIAN;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 권한 판정 규칙. 권한 계산에 쓰는 조회(그룹·개인 역할)와 배정·연결 조회는 Fake로 세운다.
 * 쿼리 자체는 {@code AccessGroupMemberRepositoryTest}·{@code AccessMemberRoleRepositoryTest}·
 * {@code CareRecipientRepositoryTest}가 검증한다.
 *
 * <pre>
 * 기관 A  관리자
 *         담당자 ──배정── 어르신(recipient) ──연결── 보호자
 *         다른 어르신(otherRecipient) — 누구와도 관계 없음
 * 기관 B  어르신(foreignRecipient)
 * </pre>
 */
class BackofficeAccessPolicyTest {

    private FakeMemberAdaptor members;
    private FakeRecipientAdaptor recipients;
    private FakeRelationAdaptor relations;
    private FakeGrantAdaptor grants;
    private BackofficeAccessPolicy policy;

    private Organization organization;
    private Organization foreignOrganization;
    private OrganizationMember admin;
    private OrganizationMember worker;
    private OrganizationMember guardian;
    private CareRecipient recipient;
    private CareRecipient otherRecipient;
    private CareRecipient foreignRecipient;

    @BeforeEach
    void setUp() {
        members = new FakeMemberAdaptor();
        recipients = new FakeRecipientAdaptor();
        relations = new FakeRelationAdaptor();
        grants = new FakeGrantAdaptor();
        policy = new BackofficeAccessPolicy(members, recipients, relations, new EffectivePermissionResolver(grants));

        organization = organization(1L);
        foreignOrganization = organization(2L);
        admin = memberWithDefaultRole(10L, organization, ORG_ADMIN);
        worker = memberWithDefaultRole(11L, organization, CARE_WORKER);
        guardian = memberWithDefaultRole(12L, organization, GUARDIAN);
        recipient = recipient(100L, organization);
        otherRecipient = recipient(101L, organization);
        foreignRecipient = recipient(200L, foreignOrganization);
        relations.assign(recipient, worker);
        relations.link(recipient, guardian);
    }

    // -- 권한표 --------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} · {1} → {2}")
    @MethodSource("com.safori.domain.access.PhotoPermissionMatrix#cells")
    @DisplayName("권한표: 기본 역할별 허용 기능과 범위(소속 기관 전체 / 현재 본인 배정 / 현재 본인 연결)")
    void photoMatrix(RoleTemplateCode role, PermissionCode permission, String expected) {
        OrganizationMember member = memberOf(role);
        Optional<DataScope> scope = PhotoPermissionMatrix.expectedScope(role, permission);
        boolean allowed = scope.isPresent();
        boolean organizationWide = scope.filter(ORGANIZATION::equals).isPresent();

        assertThat(policy.hasPermission(member.getId(), permission))
                .as("권한 보유 (%s)", expected).isEqualTo(allowed);
        assertThat(policy.canAccessRecipient(member.getId(), permission, recipient.getId()))
                .as("본인이 배정·연결된 어르신 (%s)", expected).isEqualTo(allowed);
        assertThat(policy.canAccessRecipient(member.getId(), permission, otherRecipient.getId()))
                .as("같은 기관, 관계없는 어르신 (%s)", expected).isEqualTo(organizationWide);
        assertThat(policy.canAccessRecipient(member.getId(), permission, foreignRecipient.getId()))
                .as("다른 기관 어르신").isFalse();
        assertThat(policy.canAccessOrganization(member.getId(), permission, organization.getId()))
                .as("소속 기관 단위 작업 (%s)", expected).isEqualTo(organizationWide);
        assertThat(policy.canAccessOrganization(member.getId(), permission, foreignOrganization.getId()))
                .as("다른 기관 단위 작업").isFalse();
    }

    @Test
    @DisplayName("다른 기관 관리자는 우리 기관 어르신·기관 작업에 접근할 수 없다")
    void foreignAdminIsConfinedToOwnOrganization() {
        OrganizationMember foreignAdmin = memberWithDefaultRole(20L, foreignOrganization, ORG_ADMIN);

        assertThat(policy.canAccessRecipient(foreignAdmin.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
        assertThat(policy.canAccessOrganization(foreignAdmin.getId(), RECIPIENT_CREATE, organization.getId())).isFalse();
        assertThat(policy.canAccessRecipient(foreignAdmin.getId(), RECIPIENT_READ, foreignRecipient.getId())).isTrue();
    }

    // -- 관계·상태가 바뀌면 다음 판정부터 반영된다 ------------------------------------------------

    @Test
    @DisplayName("배정이 바뀌면 이전 담당자는 조회·업무일지 작성이 거부되고 새 담당자는 허용된다")
    void reassignmentMovesAccess() {
        OrganizationMember nextWorker = memberWithDefaultRole(13L, organization, CARE_WORKER);
        relations.endAssignment(recipient, worker);
        relations.assign(recipient, nextWorker);

        assertThat(policy.canAccessRecipient(worker.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
        assertThat(policy.canAccessRecipient(worker.getId(), WORK_LOG_WRITE, recipient.getId())).isFalse();
        assertThat(policy.canAccessRecipient(nextWorker.getId(), RECIPIENT_READ, recipient.getId())).isTrue();
    }

    @Test
    @DisplayName("보호자 연결이 해제되면 보호자는 그 어르신도, 공개 상태도 볼 수 없다")
    void unlinkedGuardianLosesAccess() {
        relations.unlink(recipient, guardian);

        assertThat(policy.canAccessRecipient(guardian.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
        assertThat(policy.canAccessRecipient(guardian.getId(), GUARDIAN_STATUS_READ, recipient.getId())).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = OrganizationMemberStatus.class, names = {"PENDING", "SUSPENDED", "REVOKED"})
    @DisplayName("활성이 아닌 구성원은 기본 그룹에 들어가 있어도 모든 판정이 거부된다")
    void inactiveMemberIsDenied(OrganizationMemberStatus status) {
        OrganizationMember inactiveAdmin = member(30L, organization, status);
        grants.joinDefaultGroup(inactiveAdmin, ORG_ADMIN);

        assertThat(policy.hasPermission(inactiveAdmin.getId(), RECIPIENT_READ)).isFalse();
        assertThat(policy.canAccessRecipient(inactiveAdmin.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
        assertThat(policy.recipientScope(inactiveAdmin.getId(), RECIPIENT_READ).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("계정이 정지되거나 기관이 비활성화되면 관리자도 거부된다")
    void suspendedAccountOrInactiveOrganizationIsDenied() {
        admin.getAccount().suspend();
        assertThat(policy.hasPermission(admin.getId(), RECIPIENT_READ)).isFalse();

        organization.deactivate();
        assertThat(policy.canAccessRecipient(worker.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
    }

    @Test
    @DisplayName("비활성 어르신은 관리자도 조회할 수 없다")
    void inactiveRecipientIsDenied() {
        recipient.deactivate();

        assertThat(policy.canAccessRecipient(admin.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
        assertThat(policy.canAccessRecipient(worker.getId(), RECIPIENT_READ, recipient.getId())).isFalse();
    }

    // -- 여러 역할 ------------------------------------------------------------------------

    @Test
    @DisplayName("개인 예외 역할은 그 권한만 넓히고, 기존 역할의 데이터 범위는 그대로 둔다")
    void directRoleWidensOnlyItsPermission() {
        grants.grantDirectly(worker, ASSIGNMENT_READ, ORGANIZATION);

        assertThat(policy.canAccessOrganization(worker.getId(), ASSIGNMENT_READ, organization.getId())).isTrue();
        assertThat(policy.canAccessRecipient(worker.getId(), RECIPIENT_READ, otherRecipient.getId()))
                .as("어르신 조회 범위는 여전히 본인 배정").isFalse();
    }

    @Test
    @DisplayName("범위는 권한별로 합쳐진다 — 담당자이면서 다른 어르신의 보호자인 구성원")
    void scopesAreMergedPerPermission() {
        grants.joinDefaultGroup(worker, GUARDIAN);
        relations.link(otherRecipient, worker);

        assertThat(policy.canAccessRecipient(worker.getId(), RECIPIENT_READ, otherRecipient.getId()))
                .as("연결 범위로 조회").isTrue();
        assertThat(policy.canAccessRecipient(worker.getId(), GUARDIAN_STATUS_READ, otherRecipient.getId())).isTrue();
        assertThat(policy.canAccessRecipient(worker.getId(), CARE_REASON_READ, otherRecipient.getId()))
                .as("확인 사유는 배정 범위로만 받았다").isFalse();
    }

    // -- 목록 조회 범위 --------------------------------------------------------------------

    @Test
    @DisplayName("목록 조회 범위: 관리자는 기관 전체, 담당자는 배정, 보호자는 연결, 권한이 없으면 비어 있다")
    void recipientScopePerRole() {
        RecipientAccessScope adminScope = policy.recipientScope(admin.getId(), RECIPIENT_READ);
        assertThat(adminScope.organizationWide()).isTrue();
        assertThat(adminScope.organizationId()).isEqualTo(organization.getId());

        assertThat(policy.recipientScope(worker.getId(), RECIPIENT_READ).scopes()).containsExactly(ASSIGNED_RECIPIENT);
        assertThat(policy.recipientScope(guardian.getId(), RECIPIENT_READ).scopes()).containsExactly(LINKED_RECIPIENT);
        assertThat(policy.recipientScope(guardian.getId(), CARE_STATUS_READ).isEmpty())
                .as("보호자는 내부 상태 목록 불가").isTrue();
    }

    // -- 메서드 보안(SpEL) 진입점 -----------------------------------------------------------

    @Test
    @DisplayName("SpEL 진입점: 백오피스 인증이 아니거나 알 수 없는 권한 코드면 거부한다")
    void authenticationEntryPoints() {
        Authentication workerAuthentication =
                new UsernamePasswordAuthenticationToken(BackofficeActor.from(worker), "", List.of());
        Authentication appUser = new UsernamePasswordAuthenticationToken(
                "user01", "", List.of(new SimpleGrantedAuthority("RECIPIENT_READ")));

        assertThat(policy.canAccessRecipient(workerAuthentication, "RECIPIENT_READ", recipient.getId())).isTrue();
        assertThat(policy.canAccessRecipient(workerAuthentication, "RECIPIENT_READ", otherRecipient.getId())).isFalse();
        assertThat(policy.canAccessRecipient(workerAuthentication, "RECIPIENT_REED", recipient.getId()))
                .as("오타").isFalse();
        assertThat(policy.hasPermission(appUser, "RECIPIENT_READ")).as("어르신 앱 사용자 인증").isFalse();
        assertThat(policy.hasPermission(null, "RECIPIENT_READ")).isFalse();
    }

    // -- 픽스처 -----------------------------------------------------------------------------

    private OrganizationMember memberOf(RoleTemplateCode role) {
        return switch (role) {
            case ORG_ADMIN -> admin;
            case CARE_WORKER -> worker;
            case GUARDIAN -> guardian;
        };
    }

    /** 기본 그룹에 소속돼 템플릿 권한을 상속받은 활성 구성원. */
    private OrganizationMember memberWithDefaultRole(Long id, Organization organization, RoleTemplateCode role) {
        OrganizationMember member = member(id, organization, OrganizationMemberStatus.ACTIVE);
        grants.joinDefaultGroup(member, role);
        return member;
    }

    private OrganizationMember member(Long id, Organization organization, OrganizationMemberStatus status) {
        BackofficeAccount account = BackofficeAccount.builder()
                .id(id)
                .accountUuid("account-" + id)
                .loginId("login-" + id)
                .passwordHash("hash")
                .name("구성원 " + id)
                .status(BackofficeAccountStatus.ACTIVE)
                .authVersion(0L)
                .build();
        OrganizationMember member = OrganizationMember.builder()
                .id(id)
                .organization(organization)
                .account(account)
                .status(status)
                .build();
        members.add(member);
        return member;
    }

    private CareRecipient recipient(Long id, Organization organization) {
        CareRecipient recipient = CareRecipient.builder()
                .id(id)
                .publicId("recipient-" + id)
                .organization(organization)
                .status(CareRecipientStatus.ACTIVE)
                .build();
        recipients.add(recipient);
        return recipient;
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id)
                .publicId("organization-" + id)
                .name("기관 " + id)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private static class FakeMemberAdaptor implements OrganizationMemberAdaptor {

        private final Map<Long, OrganizationMember> members = new HashMap<>();

        void add(OrganizationMember member) {
            members.put(member.getId(), member);
        }

        @Override
        public Optional<OrganizationMember> findForAuthentication(String accountUuid, String organizationPublicId) {
            return members.values().stream()
                    .filter(member -> member.getAccount().getAccountUuid().equals(accountUuid))
                    .filter(member -> member.getOrganization().getPublicId().equals(organizationPublicId))
                    .findFirst();
        }

        @Override
        public Optional<OrganizationMember> findForAuthorization(Long memberId) {
            return Optional.ofNullable(members.get(memberId));
        }
    }

    private static class FakeRecipientAdaptor implements CareRecipientAdaptor {

        private final Map<Long, CareRecipient> recipients = new HashMap<>();

        void add(CareRecipient recipient) {
            recipients.put(recipient.getId(), recipient);
        }

        @Override
        public Optional<CareRecipient> findById(Long recipientId) {
            return Optional.ofNullable(recipients.get(recipientId));
        }

        @Override
        public Optional<CareRecipient> findByPublicId(String publicId) {
            return recipients.values().stream()
                    .filter(recipient -> recipient.getPublicId().equals(publicId))
                    .findFirst();
        }

        @Override
        public Page<CareRecipient> queryAccessible(RecipientAccessScope scope, Pageable pageable) {
            throw new UnsupportedOperationException("판정 정책은 목록 쿼리를 쓰지 않는다");
        }
    }

    private static class FakeRelationAdaptor implements CareRelationAdaptor {

        private final Set<List<Long>> assignments = new HashSet<>();
        private final Set<List<Long>> links = new HashSet<>();

        void assign(CareRecipient recipient, OrganizationMember worker) {
            assignments.add(List.of(recipient.getId(), worker.getId()));
        }

        void endAssignment(CareRecipient recipient, OrganizationMember worker) {
            assignments.remove(List.of(recipient.getId(), worker.getId()));
        }

        void link(CareRecipient recipient, OrganizationMember guardian) {
            links.add(List.of(recipient.getId(), guardian.getId()));
        }

        void unlink(CareRecipient recipient, OrganizationMember guardian) {
            links.remove(List.of(recipient.getId(), guardian.getId()));
        }

        @Override
        public boolean existsActiveAssignment(CareRecipient recipient, OrganizationMember worker) {
            return assignments.contains(List.of(recipient.getId(), worker.getId()));
        }

        @Override
        public boolean existsActiveGuardianLink(CareRecipient recipient, OrganizationMember guardian) {
            return links.contains(List.of(recipient.getId(), guardian.getId()));
        }
    }

    private static class FakeGrantAdaptor implements AccessGrantAdaptor {

        private final Map<Long, List<PermissionGrant>> inherited = new HashMap<>();
        private final Map<Long, List<PermissionGrant>> direct = new HashMap<>();

        /** 기관 생성 시 만들어지는 기본 그룹에 소속된 것처럼 템플릿 권한을 상속시킨다. */
        void joinDefaultGroup(OrganizationMember member, RoleTemplateCode template) {
            List<PermissionGrant> memberGrants = inherited.computeIfAbsent(member.getId(), id -> new ArrayList<>());
            template.getDefaultPermissions().forEach(permission ->
                    memberGrants.add(new PermissionGrant(permission.name(), template.getDataScope())));
        }

        void grantDirectly(OrganizationMember member, PermissionCode permission, DataScope scope) {
            direct.computeIfAbsent(member.getId(), id -> new ArrayList<>())
                    .add(new PermissionGrant(permission.name(), scope));
        }

        @Override
        public List<PermissionGrant> queryGrantsInheritedFromGroups(Long memberId, Long organizationId) {
            return inherited.getOrDefault(memberId, List.of());
        }

        @Override
        public List<PermissionGrant> queryGrantsFromDirectRoles(Long memberId, Long organizationId, LocalDateTime now) {
            return direct.getOrDefault(memberId, List.of());
        }
    }
}
