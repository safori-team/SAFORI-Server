package com.safori.domain.organization.service;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.care.service.CareRelationDomainService;
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

import java.util.Optional;

import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 담당자·보호자는 기관 초대 → 승인으로 가입한다.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationMemberDomainServiceImplTest {

    @Mock OrganizationMemberRepository memberRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock BackofficeAccountRepository accountRepository;
    @Mock AccessGroupDomainService accessGroupDomainService;
    @Mock CareRelationDomainService careRelationDomainService;
    @InjectMocks OrganizationMemberDomainServiceImpl memberService;

    private final Organization organization = organization(1L);
    private final OrganizationMember admin = member(10L, organization, OrganizationMemberStatus.ACTIVE);
    private final BackofficeAccount account = account(50L);

    @Test
    @DisplayName("초대하면 PENDING으로 저장하고, 선택한 기본 역할의 기본 그룹에 넣는다")
    void inviteAddsPendingMemberToSystemGroup() {
        AccessGroup workerGroup = AccessGroup.system(organization, CARE_WORKER);
        givenStored(organization);
        givenStored(account);
        given(memberRepository.existsByOrganizationAndAccount(organization, account)).willReturn(false);
        given(memberRepository.save(any(OrganizationMember.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(accessGroupDomainService.getSystemGroup(organization, CARE_WORKER)).willReturn(workerGroup);

        OrganizationMember invited = memberService.invite(organization, account, CARE_WORKER, admin);

        assertThat(invited.getStatus()).isEqualTo(OrganizationMemberStatus.PENDING);
        assertThat(invited.getInvitedBy()).isEqualTo(admin);
        verify(accessGroupDomainService).addMember(workerGroup, invited);
    }

    @Test
    @DisplayName("초대 조건은 넘겨받은 인스턴스가 아니라 다시 읽은 기관·계정 상태로 판단한다")
    void inviteChecksReloadedState() {
        Organization deactivatedMeanwhile = organization(1L);
        deactivatedMeanwhile.deactivate();
        given(organizationRepository.findById(1L)).willReturn(Optional.of(deactivatedMeanwhile));
        givenStored(account);

        assertThatThrownBy(() -> memberService.invite(organization, account, CARE_WORKER, admin))
                .isEqualTo(OrganizationHandler.INACTIVE);
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("정지된 계정, 다른 기관 초대자, 이미 소속된 계정은 초대할 수 없다")
    void invalidInvitationsAreRejected() {
        givenStored(organization);
        BackofficeAccount suspended = account(51L);
        suspended.suspend();
        givenStored(suspended);
        givenStored(account);

        assertThatThrownBy(() -> memberService.invite(organization, suspended, CARE_WORKER, admin))
                .isEqualTo(AccountHandler.INACTIVE);

        OrganizationMember foreignAdmin = member(20L, organization(3L), OrganizationMemberStatus.ACTIVE);
        assertThatThrownBy(() -> memberService.invite(organization, account, CARE_WORKER, foreignAdmin))
                .isEqualTo(OrganizationHandler.MISMATCH);

        given(memberRepository.existsByOrganizationAndAccount(organization, account)).willReturn(true);
        assertThatThrownBy(() -> memberService.invite(organization, account, CARE_WORKER, admin))
                .isEqualTo(OrganizationHandler.MEMBER_ALREADY_EXISTS);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인은 넘겨받은 인스턴스가 아니라 다시 읽은 구성원을 ACTIVE로 바꾸고, 승인자·승인 시각을 남긴다")
    void approveChangesReloadedMember() {
        OrganizationMember stored = member(11L, organization, OrganizationMemberStatus.PENDING);
        given(memberRepository.findById(11L)).willReturn(Optional.of(stored));

        OrganizationMember approved = memberService.approve(
                member(11L, organization, OrganizationMemberStatus.PENDING), admin);

        assertThat(approved).isSameAs(stored);
        assertThat(stored.getStatus()).isEqualTo(OrganizationMemberStatus.ACTIVE);
        assertThat(stored.getApprovedBy()).isEqualTo(admin);
        assertThat(stored.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("본인 가입 승인과 다른 기관 구성원의 승인은 막는다")
    void selfOrForeignApprovalIsRejected() {
        OrganizationMember pending = member(11L, organization, OrganizationMemberStatus.PENDING);
        OrganizationMember foreignAdmin = member(20L, organization(3L), OrganizationMemberStatus.ACTIVE);
        given(memberRepository.findById(11L)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> memberService.approve(pending, pending))
                .isEqualTo(OrganizationHandler.MEMBER_SELF_APPROVAL);
        assertThatThrownBy(() -> memberService.approve(pending, foreignAdmin))
                .isEqualTo(OrganizationHandler.MISMATCH);
        assertThat(pending.getStatus()).isEqualTo(OrganizationMemberStatus.PENDING);
    }

    @Test
    @DisplayName("상태 전이가 맞지 않으면 거부한다 — 활성 구성원 재승인, 소속 종료 후 재활성화·재종료")
    void invalidTransitionsAreRejected() {
        OrganizationMember worker = member(11L, organization, OrganizationMemberStatus.ACTIVE);
        given(memberRepository.findById(11L)).willReturn(Optional.of(worker));

        assertThatThrownBy(() -> memberService.approve(worker, admin))
                .isEqualTo(OrganizationHandler.MEMBER_INVALID_STATUS);

        memberService.revoke(worker, admin);
        assertThatThrownBy(() -> memberService.reactivate(worker))
                .isEqualTo(OrganizationHandler.MEMBER_INVALID_STATUS);
        assertThatThrownBy(() -> memberService.revoke(worker, admin))
                .isEqualTo(OrganizationHandler.MEMBER_INVALID_STATUS);
    }

    @Test
    @DisplayName("소속을 종료하면 다시 읽은 구성원이 REVOKED가 되고, 현재 배정·보호자 연결 종료를 함께 처리한다")
    void revokeEndsCareRelations() {
        OrganizationMember stored = member(11L, organization, OrganizationMemberStatus.ACTIVE);
        given(memberRepository.findById(11L)).willReturn(Optional.of(stored));

        OrganizationMember revoked = memberService.revoke(
                member(11L, organization, OrganizationMemberStatus.ACTIVE), admin);

        assertThat(revoked).isSameAs(stored);
        assertThat(stored.getStatus()).isEqualTo(OrganizationMemberStatus.REVOKED);
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(careRelationDomainService).endAllRelationsOf(stored, admin);
    }

    private void givenStored(Organization organization) {
        given(organizationRepository.findById(organization.getId())).willReturn(Optional.of(organization));
    }

    private void givenStored(BackofficeAccount account) {
        given(accountRepository.findById(account.getId())).willReturn(Optional.of(account));
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id).publicId("organization-" + id).name("기관 " + id).status(OrganizationStatus.ACTIVE).build();
    }

    private static OrganizationMember member(Long id, Organization organization, OrganizationMemberStatus status) {
        return OrganizationMember.builder().id(id).organization(organization).status(status).build();
    }

    private static BackofficeAccount account(Long id) {
        return BackofficeAccount.builder()
                .id(id).accountUuid("account-" + id).loginId("worker-" + id).passwordHash("ENCODED").name("이담당")
                .status(BackofficeAccountStatus.ACTIVE).authVersion(0L).build();
    }
}
