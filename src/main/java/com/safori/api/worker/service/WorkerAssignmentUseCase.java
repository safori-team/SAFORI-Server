package com.safori.api.worker.service;

import com.safori.api.recipient.service.OrganizationRecipients;
import com.safori.api.worker.dto.AssignmentResponse;
import com.safori.api.worker.dto.ManagerRecipientsResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 담당자 배정·변경·해제·일괄 변경. 모두 요청한 구성원 기관(토큰 기준)의 대상자·담당자만 다룬다.
 * 배정은 기존 배정을 종료하고 새로 만들어 이력을 남긴다(대상자당 현재 담당자 1명).
 */
@UseCase
@RequiredArgsConstructor
public class WorkerAssignmentUseCase {

    private final OrganizationWorkers organizationWorkers;
    private final OrganizationRecipients organizationRecipients;
    private final CareRelationDomainService careRelationDomainService;
    private final CareAssignmentRepository assignmentRepository;

    /** 배정·변경. 미배정이면 새로 배정하고, 다른 담당자가 있으면 바꾼다. 같은 담당자면 그대로 둔다. */
    @Transactional
    public AssignmentResponse assign(BackofficeActor actor, String careRecipientId, String managerId) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        OrganizationMember worker = organizationWorkers.get(actor, managerId);
        careRelationDomainService.assignWorker(recipient, worker, organizationWorkers.actorOf(actor), null);
        return new AssignmentResponse(recipient.getPublicId(), managerId);
    }

    /** 배정 해제(미배정으로). 배정이 없으면 아무것도 하지 않는다. */
    @Transactional
    public AssignmentResponse unassign(BackofficeActor actor, String careRecipientId) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        careRelationDomainService.endAssignment(recipient, organizationWorkers.actorOf(actor));
        return new AssignmentResponse(recipient.getPublicId(), null);
    }

    /**
     * 담당자의 배정 대상자를 요청 목록과 똑같이 맞춘다. 목록에서 빠진 현재 대상자는 배정 해제,
     * 새로 들어온 대상자는 이 담당자로 배정(다른 담당자였으면 옮김)한다.
     */
    @Transactional
    public ManagerRecipientsResponse replace(BackofficeActor actor, String managerId, List<String> careRecipientIds) {
        OrganizationMember worker = organizationWorkers.get(actor, managerId);
        OrganizationMember actorMember = organizationWorkers.actorOf(actor);
        Set<String> wanted = new LinkedHashSet<>(careRecipientIds);
        // 대상자 확인을 먼저 끝내 잘못된 식별자가 섞이면 아무것도 바꾸지 않는다.
        List<CareRecipient> targets = wanted.stream().map(id -> organizationRecipients.get(actor, id)).toList();

        // ponytail: 현재 배정은 잠금 없이 읽는다. 그 사이 다른 요청이 대상자를 옮기면 옮겨진 배정을 해제할 수 있다.
        // 관리자 한 명이 하는 드문 작업이라 두고, 문제가 되면 해제 대상도 담당자 일치를 확인하며 잠근다.
        assignmentRepository.findCurrentByWorker(worker).stream()
                .map(CareAssignment::getRecipient)
                .filter(recipient -> !wanted.contains(recipient.getPublicId()))
                .forEach(recipient -> careRelationDomainService.endAssignment(recipient, actorMember));
        targets.forEach(recipient -> careRelationDomainService.assignWorker(recipient, worker, actorMember, null));
        return new ManagerRecipientsResponse(managerId, List.copyOf(wanted));
    }
}
