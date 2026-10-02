package com.safori.api.recipient.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.recipient.dto.RecipientAssignmentFilter;
import com.safori.api.recipient.dto.RecipientListResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.DataScope;
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

import java.util.Set;

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
        return query(accessPolicy.recipientScope(actor.organizationMemberId(), PermissionCode.RECIPIENT_READ),
                statusCode, assignment, keyword, managerId, page, size);
    }

    /** 운영자 콘솔: 기관의 대상자 전체(권한 범위 없이). 형식은 {@link #execute}와 같다. */
    @Transactional(readOnly = true)
    public RecipientListResponse forOperator(Long organizationId, CareStatusCode statusCode, String keyword,
                                             int page, int size) {
        return query(new RecipientAccessScope(organizationId, null, Set.of(DataScope.ORGANIZATION)),
                statusCode, RecipientAssignmentFilter.ALL, keyword, null, page, size);
    }

    private RecipientListResponse query(RecipientAccessScope scope, CareStatusCode statusCode,
                                        RecipientAssignmentFilter assignment, String keyword, String managerId,
                                        int page, int size) {
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

    /**
     * 보호자 홈: 연결된 대상자. 응답 형식은 {@link #execute}와 같고, 보호자 권한(GUARDIAN_STATUS_READ) 밖의 값 —
     * 현재 상태 코드·사유·처리 상태·담당자·최근 안부 확인 — 은 비운다. 탭 개수는 전체만 채운다.
     */
    @Transactional(readOnly = true)
    public RecipientListResponse forGuardian(BackofficeActor actor, int page, int size) {
        RecipientAccessScope scope = accessPolicy.recipientScope(actor.organizationMemberId(),
                PermissionCode.GUARDIAN_STATUS_READ);
        PageRequest pageable = PageRequest.of(page - 1, size);
        if (scope.isEmpty()) {
            return new RecipientListResponse(new RecipientListResponse.Counts(0, 0, 0, 0, 0, 0),
                    PagedResponse.from(Page.empty(pageable)));
        }
        Page<RecipientListResponse.Item> recipients = recipientRepository.findStatusBoard(
                        scope.organizationId(), scope.organizationMemberId(), scope.organizationWide(),
                        scope.includesAssigned(), scope.includesLinked(), null, null, null, null, pageable)
                .map(row -> new RecipientListResponse.Item(row.recipientPublicId(), row.name(), row.birthDate(),
                        null, null, null, null, null, null, null));
        return new RecipientListResponse(
                new RecipientListResponse.Counts(recipients.getTotalElements(), 0, 0, 0, 0, 0),
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
