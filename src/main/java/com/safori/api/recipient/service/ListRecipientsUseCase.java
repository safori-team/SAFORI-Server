package com.safori.api.recipient.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.recipient.dto.RecipientAssignmentFilter;
import com.safori.api.recipient.dto.RecipientListResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.policy.BackofficeAccessPolicy;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.access.policy.RecipientAccessScope;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.care.model.RecipientStatusCounts;
import com.safori.domain.care.model.RecipientStatusRow;
import com.safori.domain.care.repository.CareRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 대상자 현황(상태 코드 탭)과 대상자 목록(배정 탭)이 같이 쓴다. 범위는 요청한 구성원의 권한 범위로 정해진다
 * — 관리자는 기관 전체, 담당자는 본인 배정 대상자만.
 */
@UseCase
@RequiredArgsConstructor
public class ListRecipientsUseCase {

    private final BackofficeAccessPolicy accessPolicy;
    private final CareRecipientRepository recipientRepository;

    @Transactional(readOnly = true)
    public RecipientListResponse execute(BackofficeActor actor, CareStatusCode statusCode,
                                         RecipientAssignmentFilter assignment, String keyword, String managerId,
                                         int page, int size) {
        RecipientAccessScope scope = accessPolicy.recipientScope(actor.organizationMemberId(), PermissionCode.RECIPIENT_READ);
        PageRequest pageable = PageRequest.of(page - 1, size);
        if (scope.isEmpty()) {
            return new RecipientListResponse(new RecipientListResponse.Counts(0, 0, 0, 0, 0, 0),
                    PagedResponse.from(Page.empty(pageable)));
        }
        String name = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String manager = StringUtils.hasText(managerId) ? managerId : null;

        Page<RecipientListResponse.Item> recipients = recipientRepository.findStatusBoard(
                        scope.organizationId(), scope.organizationMemberId(), scope.organizationWide(),
                        scope.includesAssigned(), scope.includesLinked(), name, manager, statusCode,
                        assignment.assigned(), pageable)
                .map(ListRecipientsUseCase::toItem);
        RecipientStatusCounts c = recipientRepository.countStatusBoard(
                scope.organizationId(), scope.organizationMemberId(), scope.organizationWide(),
                scope.includesAssigned(), scope.includesLinked(), name, manager);
        return new RecipientListResponse(
                new RecipientListResponse.Counts(c.total(), c.assigned(), c.total() - c.assigned(),
                        c.urgent(), c.caution(), c.interest()),
                PagedResponse.from(recipients));
    }

    private static RecipientListResponse.Item toItem(RecipientStatusRow row) {
        RecipientListResponse.Manager manager = row.managerAccountUuid() == null ? null
                : new RecipientListResponse.Manager(row.managerAccountUuid(), row.managerName());
        return new RecipientListResponse.Item(row.recipientPublicId(), row.name(), row.birthDate(), row.statusCode(),
                row.reasonMessage(), row.processingStatus(), row.detectedAt(), row.recordPublicId(), manager,
                row.lastCheckedAt());
    }
}
