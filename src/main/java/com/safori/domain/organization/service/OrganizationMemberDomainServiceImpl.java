package com.safori.domain.organization.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
@DomainService
@RequiredArgsConstructor
public class OrganizationMemberDomainServiceImpl implements OrganizationMemberDomainService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final BackofficeAccountRepository accountRepository;
    private final AccessGroupDomainService accessGroupDomainService;
    private final CareRelationDomainService careRelationDomainService;

    @Override
    public OrganizationMember invite(Organization organization, BackofficeAccount account,
                                     RoleTemplateCode initialRole, OrganizationMember invitedBy) {
        boolean admin = initialRole == RoleTemplateCode.ORG_ADMIN;
        Organization currentOrganization = (admin
                ? organizationRepository.findByIdForUpdate(organization.getId())
                : organizationRepository.findById(organization.getId()))
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 기관입니다: " + organization.getId()));
        BackofficeAccount currentAccount = accountRepository.findById(account.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 백오피스 계정입니다: " + account.getId()));
        if (!currentOrganization.isActive()) {
            throw OrganizationHandler.INACTIVE;
        }
        if (!currentAccount.isActive()) {
            throw AccountHandler.INACTIVE;
        }
        Organization.requireMembers(currentOrganization, invitedBy);
        if (memberRepository.existsByOrganizationAndAccount(currentOrganization, currentAccount)) {
            throw OrganizationHandler.MEMBER_ALREADY_EXISTS;
        }

        AccessGroup group = accessGroupDomainService.getSystemGroup(currentOrganization, initialRole);
        // 기관 관리자는 1명. 동시 초대는 위의 기관 행 잠금으로 직렬화된다.
        if (admin && accessGroupDomainService.hasCurrentMember(group)) {
            throw OrganizationHandler.ADMIN_ALREADY_EXISTS;
        }

        OrganizationMember member = memberRepository.save(
                OrganizationMember.invite(currentOrganization, currentAccount, invitedBy));
        accessGroupDomainService.addMember(group, member);
        return member;
    }

    @Override
    public OrganizationMember approve(OrganizationMember member, OrganizationMember approver) {
        OrganizationMember current = reload(member);
        Organization.requireMembers(current.getOrganization(), approver);
        if (current.isSameMember(approver)) {
            throw OrganizationHandler.MEMBER_SELF_APPROVAL;
        }
        current.approve(approver, LocalDateTime.now());
        return current;
    }

    @Override
    public OrganizationMember suspend(OrganizationMember member) {
        OrganizationMember current = reload(member);
        current.suspend();
        return current;
    }

    @Override
    public OrganizationMember reactivate(OrganizationMember member) {
        OrganizationMember current = reload(member);
        current.reactivate();
        return current;
    }

    @Override
    public OrganizationMember revoke(OrganizationMember member, OrganizationMember revokedBy) {
        OrganizationMember current = reload(member);
        Organization.requireMembers(current.getOrganization(), revokedBy);
        current.revoke(LocalDateTime.now());
        careRelationDomainService.endAllRelationsOf(current, revokedBy);
        return current;
    }

    /** 다른 트랜잭션에서 읽은 인스턴스를 바꾸면 저장되지 않으므로, 이 트랜잭션에서 다시 읽은 구성원을 바꾼다. */
    private OrganizationMember reload(OrganizationMember member) {
        return memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 구성원입니다: " + member.getId()));
    }
}
