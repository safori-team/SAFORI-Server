package com.safori.api.recipient.service;

import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.adaptor.CareRecipientAdaptor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.care.repository.CareAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 경로의 {@code careRecipientId}(public_id)를 요청한 구성원 기관의 대상자로 찾는다. 없거나 다른 기관이면 4454.
 */
@Component
@RequiredArgsConstructor
public class OrganizationRecipients {

    private final CareRecipientAdaptor recipientAdaptor;
    private final CareAssignmentRepository assignmentRepository;

    public CareRecipient get(BackofficeActor actor, String careRecipientId) {
        return recipientAdaptor.findByPublicId(careRecipientId)
                .filter(recipient -> recipient.getOrganization().getId().equals(actor.organizationId()))
                .orElseThrow(() -> CareHandler.RECIPIENT_NOT_FOUND);
    }

    /**
     * 처리 상태 변경·일지 작성처럼 대상자 관리 이력을 남기는 작업은 현재 담당자만 한다(관리자도 불가, 4461).
     * 권한(CARE_TASK_COMPLETE·WORK_LOG_WRITE)은 관리자에게도 있어 권한 규칙만으로는 막히지 않는다.
     */
    public CareRecipient getForCurrentWorker(BackofficeActor actor, String careRecipientId) {
        CareRecipient recipient = get(actor, careRecipientId);
        boolean currentWorker = assignmentRepository.findCurrentByRecipient(recipient).stream()
                .anyMatch(assignment -> assignment.getWorker().getId().equals(actor.organizationMemberId()));
        if (!currentWorker) {
            throw CareHandler.NOT_CURRENT_WORKER;
        }
        return recipient;
    }
}
