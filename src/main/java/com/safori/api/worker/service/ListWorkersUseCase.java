package com.safori.api.worker.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.worker.dto.ManagerListResponse;
import com.safori.api.worker.dto.ManagerStatusFilter;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.organization.model.StatusCount;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 요청한 구성원 기관의 담당자 목록. 담당자 목록 화면과 담당자 변경(선택) 화면이 같이 쓴다.
 */
@UseCase
@RequiredArgsConstructor
public class ListWorkersUseCase {

    private final OrganizationMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public ManagerListResponse execute(BackofficeActor actor, ManagerStatusFilter status, String keyword,
                                       int page, int size) {
        String name = StringUtils.hasText(keyword) ? keyword.trim() : null;
        var managers = memberRepository.findWorkers(actor.organizationId(), name, status.accountStatus(),
                        PageRequest.of(page - 1, size))
                .map(w -> new ManagerListResponse.Item(w.accountUuid(), w.name(), w.jobTitle(),
                        w.accountStatus() == BackofficeAccountStatus.ACTIVE, w.assignedCount()));

        List<StatusCount> counts = memberRepository.countWorkersByStatus(actor.organizationId(), name);
        long active = countOf(counts, BackofficeAccountStatus.ACTIVE);
        long total = counts.stream().mapToLong(StatusCount::count).sum();
        return new ManagerListResponse(new ManagerListResponse.Counts(total, active, total - active),
                PagedResponse.from(managers));
    }

    private static long countOf(List<StatusCount> counts, BackofficeAccountStatus status) {
        return counts.stream().filter(c -> c.status() == status).mapToLong(StatusCount::count).sum();
    }
}
