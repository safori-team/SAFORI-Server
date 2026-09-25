package com.safori.api.worker.service;

import com.safori.api.worker.dto.ManagerDetailResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 담당자 상세. 현재 배정 대상자의 이름·생년월일은 어르신 계정(users)에서 읽는다(대상자는 user_id로만 참조).
 */
@UseCase
@RequiredArgsConstructor
public class GetWorkerUseCase {

    private final OrganizationWorkers organizationWorkers;
    private final CareAssignmentRepository assignmentRepository;
    private final UserAdaptor userAdaptor;

    @Transactional(readOnly = true)
    public ManagerDetailResponse execute(BackofficeActor actor, String managerId) {
        OrganizationMember worker = organizationWorkers.get(actor, managerId);
        BackofficeAccount account = worker.getAccount();

        List<CareRecipient> recipients = assignmentRepository.findCurrentByWorker(worker).stream()
                .map(CareAssignment::getRecipient)
                .toList();
        Map<Long, User> users = userAdaptor.queryUsersByIds(recipients.stream()
                        .map(CareRecipient::getUserId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<ManagerDetailResponse.AssignedRecipient> assigned = recipients.stream()
                .map(recipient -> {
                    User user = recipient.getUserId() == null ? null : users.get(recipient.getUserId());
                    return new ManagerDetailResponse.AssignedRecipient(recipient.getPublicId(),
                            user == null ? null : user.getName(), user == null ? null : user.getBirthDate());
                })
                .sorted(Comparator.comparing(ManagerDetailResponse.AssignedRecipient::name,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ManagerDetailResponse.AssignedRecipient::careRecipientId))
                .toList();

        return new ManagerDetailResponse(account.getAccountUuid(), account.getLoginId(), account.getName(),
                account.getPhone(), worker.getJobTitle(), account.isActive(), worker.getOrganization().getName(),
                RoleTemplateCode.CARE_WORKER.name(), assigned.size(), assigned);
    }
}
