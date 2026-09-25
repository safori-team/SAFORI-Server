package com.safori.domain.care.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.policy.EffectivePermissionResolver;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.safori.domain.care.exception.CareHandler.GUARDIAN_NOT_LINKABLE;
import static com.safori.domain.care.exception.CareHandler.RECIPIENT_ALREADY_REGISTERED;
import static com.safori.domain.care.exception.CareHandler.RECIPIENT_INACTIVE;
import static com.safori.domain.care.exception.CareHandler.WORKER_NOT_ASSIGNABLE;

@Transactional
@DomainService
@RequiredArgsConstructor
public class CareRelationDomainServiceImpl implements CareRelationDomainService {

    private final CareRecipientRepository recipientRepository;
    private final CareAssignmentRepository assignmentRepository;
    private final GuardianRecipientLinkRepository guardianLinkRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final EffectivePermissionResolver permissionResolver;

    @Override
    public CareRecipient registerRecipient(Organization organization, Long userId) {
        Organization current = organizationRepository.findById(organization.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 기관입니다: " + organization.getId()));
        if (!current.isActive()) {
            throw OrganizationHandler.INACTIVE;
        }
        if (userId != null && recipientRepository.existsByUserId(userId)) {
            throw RECIPIENT_ALREADY_REGISTERED;
        }
        return recipientRepository.save(CareRecipient.register(current, userId));
    }

    @Override
    public CareAssignment assignWorker(CareRecipient recipient, OrganizationMember worker,
                                       OrganizationMember assignedBy, String reason) {
        CareRecipient locked = lockActive(recipient);
        OrganizationMember currentWorker = reload(worker);
        Organization.requireMembers(locked.getOrganization(), currentWorker, assignedBy);
        if (!permissionResolver.resolve(currentWorker).hasScope(DataScope.ASSIGNED_RECIPIENT)) {
            throw WORKER_NOT_ASSIGNABLE;
        }

        List<CareAssignment> current = assignmentRepository.findCurrentByRecipientForUpdate(locked);
        if (current.size() == 1 && current.get(0).isAssignedTo(currentWorker)) {
            return current.get(0);
        }
        LocalDateTime now = LocalDateTime.now();
        current.forEach(assignment -> assignment.end(assignedBy, now));
        return assignmentRepository.save(CareAssignment.start(locked, currentWorker, assignedBy, reason, now));
    }

    @Override
    public void endAssignment(CareRecipient recipient, OrganizationMember endedBy) {
        CareRecipient locked = lock(recipient);
        Organization.requireMembers(locked.getOrganization(), endedBy);
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.findCurrentByRecipientForUpdate(locked)
                .forEach(assignment -> assignment.end(endedBy, now));
    }

    @Override
    public GuardianRecipientLink linkGuardian(CareRecipient recipient, OrganizationMember guardian,
                                              OrganizationMember linkedBy) {
        CareRecipient locked = lockActive(recipient);
        OrganizationMember currentGuardian = reload(guardian);
        Organization.requireMembers(locked.getOrganization(), currentGuardian, linkedBy);
        if (!permissionResolver.resolve(currentGuardian).hasScope(DataScope.LINKED_RECIPIENT)) {
            throw GUARDIAN_NOT_LINKABLE;
        }

        List<GuardianRecipientLink> current =
                guardianLinkRepository.findCurrentByRecipientAndGuardianForUpdate(locked, currentGuardian);
        if (!current.isEmpty()) {
            return current.get(0);
        }
        return guardianLinkRepository.save(
                GuardianRecipientLink.start(locked, currentGuardian, linkedBy, LocalDateTime.now()));
    }

    @Override
    public void unlinkGuardian(CareRecipient recipient, OrganizationMember guardian, OrganizationMember endedBy) {
        CareRecipient locked = lock(recipient);
        Organization.requireMembers(locked.getOrganization(), guardian, endedBy);
        LocalDateTime now = LocalDateTime.now();
        guardianLinkRepository.findCurrentByRecipientAndGuardianForUpdate(locked, guardian)
                .forEach(link -> link.end(endedBy, now));
    }

    @Override
    public void endAllRelationsOf(OrganizationMember member, OrganizationMember endedBy) {
        Organization.requireMembers(member.getOrganization(), endedBy);
        LocalDateTime now = LocalDateTime.now();
        assignmentRepository.findCurrentByWorkerForUpdate(member)
                .forEach(assignment -> assignment.end(endedBy, now));
        guardianLinkRepository.findCurrentByGuardianForUpdate(member)
                .forEach(link -> link.end(endedBy, now));
    }

    private CareRecipient lockActive(CareRecipient recipient) {
        CareRecipient locked = lock(recipient);
        if (!locked.isActive()) {
            throw RECIPIENT_INACTIVE;
        }
        return locked;
    }

    private CareRecipient lock(CareRecipient recipient) {
        return recipientRepository.findByIdForUpdate(recipient.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 어르신입니다: " + recipient.getId()));
    }

    /** 넘겨받은 인스턴스가 옛 상태이거나 준영속이어도, 배정·연결 가능 여부는 현재 상태로 판단한다. */
    private OrganizationMember reload(OrganizationMember member) {
        return memberRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 구성원입니다: " + member.getId()));
    }
}
