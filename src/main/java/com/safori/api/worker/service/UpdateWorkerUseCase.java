package com.safori.api.worker.service;

import com.safori.api.worker.dto.UpdateWorkerRequest;
import com.safori.api.worker.dto.WorkerProfileResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기관(토큰 기준)의 담당자 정보를 수정한다. 다른 기관 구성원이거나 담당자가 아니면 없는 구성원으로 본다(4307).
 */
@UseCase
@RequiredArgsConstructor
public class UpdateWorkerUseCase {

    private final BackofficeAccountRepository accountRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessGroupDomainService accessGroupDomainService;
    private final BackofficeAccountDomainService accountDomainService;

    @Transactional
    public WorkerProfileResponse execute(BackofficeActor actor, String managerId, UpdateWorkerRequest request) {
        OrganizationMember worker = accountRepository.findByAccountUuid(managerId)
                .flatMap(memberRepository::findCurrentByAccount)
                .filter(member -> member.getOrganization().getId().equals(actor.organizationId()))
                .filter(member -> accessGroupDomainService.primaryTemplateOf(member)
                        .filter(RoleTemplateCode.CARE_WORKER::equals).isPresent())
                .orElseThrow(() -> OrganizationHandler.MEMBER_NOT_FOUND);

        BackofficeAccount account = accountDomainService.changeProfile(worker.getAccount(), request.name(), request.phone());
        worker.changeJobTitle(request.jobTitle());
        // 상태가 바뀔 때만 전환한다(정지는 발급된 토큰도 끊으므로 이미 정지된 계정에 다시 걸지 않는다).
        if (request.active() && !account.isActive()) {
            account = accountDomainService.activate(account);
        } else if (!request.active() && account.isActive()) {
            account = accountDomainService.suspend(account);
        }
        return new WorkerProfileResponse(account.getAccountUuid(), account.getLoginId(), account.getName(),
                account.getPhone(), worker.getJobTitle(), account.isActive());
    }
}
