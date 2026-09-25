package com.safori.domain.care.service;

import com.safori.domain.access.policy.EffectivePermissionResolver;
import com.safori.domain.access.policy.EffectivePermissions;
import com.safori.domain.access.policy.PermissionGrant;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecipientStatus;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.entity.OrganizationStatus;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.LINKED_RECIPIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 담당자·보호자의 데이터 범위를 만드는 관계(배정, 보호자 연결)의 규칙.
 */
@ExtendWith(MockitoExtension.class)
class CareRelationDomainServiceImplTest {

    private static final EffectivePermissions WORKER_PERMISSIONS =
            EffectivePermissions.of(List.of(new PermissionGrant("RECIPIENT_READ", ASSIGNED_RECIPIENT)));
    private static final EffectivePermissions GUARDIAN_PERMISSIONS =
            EffectivePermissions.of(List.of(new PermissionGrant("RECIPIENT_READ", LINKED_RECIPIENT)));

    @Mock CareRecipientRepository recipientRepository;
    @Mock CareAssignmentRepository assignmentRepository;
    @Mock GuardianRecipientLinkRepository guardianLinkRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberRepository memberRepository;
    @Mock EffectivePermissionResolver permissionResolver;
    @InjectMocks CareRelationDomainServiceImpl careRelationService;

    private final Organization organization = organization(1L);
    private final OrganizationMember admin = member(10L, organization);
    private final OrganizationMember worker = member(11L, organization);
    private final OrganizationMember guardian = member(12L, organization);
    private final CareRecipient recipient = CareRecipient.builder()
            .id(100L).publicId("recipient-100").organization(organization).status(CareRecipientStatus.ACTIVE).build();

    @Test
    @DisplayName("재배정하면 현재 배정을 종료하고 새 담당자 배정을 저장한다")
    void reassignmentEndsCurrentAssignment() {
        OrganizationMember nextWorker = member(13L, organization);
        CareAssignment current = CareAssignment.start(recipient, worker, admin, "최초 배정", LocalDateTime.now().minusDays(1));
        givenLocked(recipient);
        givenStored(nextWorker);
        given(permissionResolver.resolve(nextWorker)).willReturn(WORKER_PERMISSIONS);
        given(assignmentRepository.findCurrentByRecipientForUpdate(recipient)).willReturn(List.of(current));
        given(assignmentRepository.save(any(CareAssignment.class))).willAnswer(invocation -> invocation.getArgument(0));

        CareAssignment next = careRelationService.assignWorker(recipient, nextWorker, admin, "담당자 변경");

        assertThat(current.isActive()).isFalse();
        assertThat(current.getEndedBy()).isEqualTo(admin);
        assertThat(next.getWorker()).isEqualTo(nextWorker);
        assertThat(next.isActive()).isTrue();
    }

    @Test
    @DisplayName("같은 담당자를 다시 배정하면 기존 배정을 그대로 돌려준다(배정 ID 유지)")
    void sameWorkerAssignmentIsIdempotent() {
        CareAssignment current = CareAssignment.start(recipient, worker, admin, null, LocalDateTime.now());
        givenLocked(recipient);
        givenStored(worker);
        given(permissionResolver.resolve(worker)).willReturn(WORKER_PERMISSIONS);
        given(assignmentRepository.findCurrentByRecipientForUpdate(recipient)).willReturn(List.of(current));

        assertThat(careRelationService.assignWorker(recipient, worker, admin, null)).isSameAs(current);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("배정 가능 여부는 넘겨받은 인스턴스가 아니라 다시 읽은 현재 상태로 판단한다 — 그사이 정지된 담당자는 거부")
    void assignabilityUsesReloadedWorker() {
        OrganizationMember staleWorker = member(11L, organization);
        OrganizationMember suspendedMeanwhile = member(11L, organization);
        suspendedMeanwhile.suspend();
        givenLocked(recipient);
        given(memberRepository.findById(11L)).willReturn(Optional.of(suspendedMeanwhile));
        given(permissionResolver.resolve(suspendedMeanwhile)).willReturn(EffectivePermissions.none());

        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, staleWorker, admin, null))
                .isEqualTo(CareHandler.WORKER_NOT_ASSIGNABLE);
        verify(permissionResolver, never()).resolve(staleWorker);
    }

    @Test
    @DisplayName("배정 범위 권한이 없는 구성원(보호자 등)은 담당자로 배정할 수 없다")
    void memberWithoutAssignedScopeCannotBeAssigned() {
        givenLocked(recipient);
        givenStored(guardian);
        given(permissionResolver.resolve(guardian)).willReturn(GUARDIAN_PERMISSIONS);

        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, guardian, admin, null))
                .isEqualTo(CareHandler.WORKER_NOT_ASSIGNABLE);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 기관의 담당자·처리자는 배정할 수 없다")
    void foreignMembersCannotBeAssigned() {
        OrganizationMember foreignWorker = member(20L, organization(2L));
        givenLocked(recipient);
        givenStored(foreignWorker);
        givenStored(worker);

        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, foreignWorker, admin, null))
                .isEqualTo(OrganizationHandler.MISMATCH);
        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, worker, foreignWorker, null))
                .isEqualTo(OrganizationHandler.MISMATCH);
    }

    @Test
    @DisplayName("비활성 어르신에게는 배정·연결할 수 없다")
    void inactiveRecipientRejectsRelations() {
        recipient.deactivate();
        givenLocked(recipient);

        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, worker, admin, null))
                .isEqualTo(CareHandler.RECIPIENT_INACTIVE);
        assertThatThrownBy(() -> careRelationService.linkGuardian(recipient, guardian, admin))
                .isEqualTo(CareHandler.RECIPIENT_INACTIVE);
    }

    @Test
    @DisplayName("보호자 연결: 연결 범위 권한이 있으면 새로 연결하고, 이미 연결돼 있으면 그대로 돌려준다")
    void linkGuardian() {
        givenLocked(recipient);
        givenStored(guardian);
        given(permissionResolver.resolve(guardian)).willReturn(GUARDIAN_PERMISSIONS);
        given(guardianLinkRepository.findCurrentByRecipientAndGuardianForUpdate(recipient, guardian))
                .willReturn(List.of());
        given(guardianLinkRepository.save(any(GuardianRecipientLink.class))).willAnswer(invocation -> invocation.getArgument(0));

        GuardianRecipientLink link = careRelationService.linkGuardian(recipient, guardian, admin);
        assertThat(link.getGuardian()).isEqualTo(guardian);
        assertThat(link.isActive()).isTrue();

        given(guardianLinkRepository.findCurrentByRecipientAndGuardianForUpdate(recipient, guardian))
                .willReturn(List.of(link));
        assertThat(careRelationService.linkGuardian(recipient, guardian, admin)).isSameAs(link);
    }

    @Test
    @DisplayName("연결 범위 권한이 없는 담당자는 보호자로 연결할 수 없다")
    void workerCannotBeLinkedAsGuardian() {
        givenLocked(recipient);
        givenStored(worker);
        given(permissionResolver.resolve(worker)).willReturn(WORKER_PERMISSIONS);

        assertThatThrownBy(() -> careRelationService.linkGuardian(recipient, worker, admin))
                .isEqualTo(CareHandler.GUARDIAN_NOT_LINKABLE);
        verify(guardianLinkRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 기관(다시 읽은 상태 기준)에는 등록할 수 없고, 이미 어느 기관에든 등록된 앱 사용자는 다시 등록할 수 없다")
    void invalidRegistrationsAreRejected() {
        Organization deactivatedMeanwhile = organization(2L);
        deactivatedMeanwhile.deactivate();
        given(organizationRepository.findById(2L)).willReturn(Optional.of(deactivatedMeanwhile));
        assertThatThrownBy(() -> careRelationService.registerRecipient(organization(2L), 42L))
                .isEqualTo(OrganizationHandler.INACTIVE);

        given(organizationRepository.findById(1L)).willReturn(Optional.of(organization));
        given(recipientRepository.existsByUserId(42L)).willReturn(true);
        assertThatThrownBy(() -> careRelationService.registerRecipient(organization, 42L))
                .isEqualTo(CareHandler.RECIPIENT_ALREADY_REGISTERED);
        verify(recipientRepository, never()).save(any());
    }

    @Test
    @DisplayName("구성원의 현재 배정·보호자 연결을 모두 종료한다(소속 종료 시)")
    void endAllRelationsOfMember() {
        CareAssignment assignment = CareAssignment.start(recipient, worker, admin, null, LocalDateTime.now());
        GuardianRecipientLink link = GuardianRecipientLink.start(recipient, worker, admin, LocalDateTime.now());
        given(assignmentRepository.findCurrentByWorkerForUpdate(worker)).willReturn(List.of(assignment));
        given(guardianLinkRepository.findCurrentByGuardianForUpdate(worker)).willReturn(List.of(link));

        careRelationService.endAllRelationsOf(worker, admin);

        assertThat(assignment.isActive()).isFalse();
        assertThat(link.isActive()).isFalse();
        assertThat(assignment.getEndedBy()).isEqualTo(admin);
    }

    private void givenLocked(CareRecipient recipient) {
        given(recipientRepository.findByIdForUpdate(recipient.getId())).willReturn(Optional.of(recipient));
    }

    private void givenStored(OrganizationMember member) {
        given(memberRepository.findById(member.getId())).willReturn(Optional.of(member));
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id).publicId("organization-" + id).name("기관 " + id).status(OrganizationStatus.ACTIVE).build();
    }

    private static OrganizationMember member(Long id, Organization organization) {
        return OrganizationMember.builder()
                .id(id).organization(organization).status(OrganizationMemberStatus.ACTIVE).build();
    }
}
