package com.safori.api.recipient.service;

import com.safori.api.journal.service.CareJournalUseCase;
import com.safori.api.recipient.dto.RecipientDetailResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 대상자 상세(기록 상세 화면·대상자 정보 상세 화면). 상태 코드가 X인 대상자도 열 수 있도록 대상자 기준으로 조회하고,
 * 현재 기록이 있을 때만 확인 사유를 채운다. 최근 조치 기록은 일지(확인 일시 최신순)다.
 */
@UseCase
@RequiredArgsConstructor
public class GetRecipientUseCase {

    private final OrganizationRecipients organizationRecipients;
    private final CareAssignmentRepository assignmentRepository;
    private final GuardianRecipientLinkRepository linkRepository;
    private final UserAdaptor userAdaptor;
    private final CareJournalUseCase careJournalUseCase;

    @Transactional(readOnly = true)
    public RecipientDetailResponse execute(BackofficeActor actor, String careRecipientId) {
        return detail(organizationRecipients.get(actor, careRecipientId));
    }

    @Transactional(readOnly = true)
    public RecipientDetailResponse detail(CareRecipient recipient) {
        User user = recipient.getUserId() == null ? null : userAdaptor.queryUserById(recipient.getUserId());
        CareRecord current = recipient.getCurrentRecord();

        return new RecipientDetailResponse(
                recipient.getPublicId(),
                user == null ? null : user.getName(),
                user == null ? null : user.getBirthDate(),
                user == null ? null : user.getPhone(),
                user == null ? null : user.getUsername(),
                recipient.isActive(),
                user == null ? null : user.getCreatedDate(),
                currentManager(recipient),
                guardians(recipient),
                current == null ? null : current.getStatusCode(),
                current == null ? null : current.getProcessingStatus(),
                current == null ? null : new RecipientDetailResponse.Reason(current.getPublicId(),
                        current.getReasonType(), current.getReasonType().title(), current.getReasonMessage(),
                        current.getStatusCode().guidanceLabel(), current.getReasonType().guidance(),
                        current.getDetectedAt()),
                careJournalUseCase.recent(recipient));
    }

    private RecipientDetailResponse.Manager currentManager(CareRecipient recipient) {
        return assignmentRepository.findCurrentByRecipient(recipient).stream()
                .findFirst()
                .map(CareAssignment::getWorker)
                .map(worker -> {
                    BackofficeAccount account = worker.getAccount();
                    return new RecipientDetailResponse.Manager(account.getAccountUuid(), account.getName(),
                            worker.getJobTitle(), account.getPhone());
                })
                .orElse(null);
    }

    private List<RecipientDetailResponse.Guardian> guardians(CareRecipient recipient) {
        return linkRepository.findCurrentByRecipient(recipient).stream()
                .map(link -> {
                    BackofficeAccount account = link.getGuardian().getAccount();
                    return new RecipientDetailResponse.Guardian(account.getAccountUuid(), account.getName(),
                            link.getRelation(), link.relationLabel(), account.getPhone());
                })
                .toList();
    }
}
