package com.safori.api.worker.service;

import com.safori.api.worker.dto.RegisterWorkerRequest;
import com.safori.api.worker.dto.RegisterWorkerResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 담당자 계정을 만들어 자기 기관(토큰 기준)에 바로 소속시킨다. 초대·승인을 한 번에 처리해 ACTIVE가 된다.
 * 한 트랜잭션이라 중간에 실패(아이디 중복 등)하면 계정도 남지 않는다.
 */
@UseCase
@RequiredArgsConstructor
public class RegisterWorkerUseCase {

    private final BackofficeAccountDomainService accountDomainService;
    private final OrganizationMemberDomainService memberDomainService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    @Transactional
    public RegisterWorkerResponse execute(BackofficeActor actor, RegisterWorkerRequest request) {
        OrganizationMember registeredBy = memberRepository.getReferenceById(actor.organizationMemberId());
        BackofficeAccount account = accountDomainService.register(
                request.loginId(), request.password(), request.name(), request.phone());
        OrganizationMember worker = memberDomainService.invite(
                organizationRepository.getReferenceById(actor.organizationId()), account,
                RoleTemplateCode.CARE_WORKER, registeredBy);
        memberDomainService.approve(worker, registeredBy).changeJobTitle(request.jobTitle());
        if (!request.active()) {
            account = accountDomainService.suspend(account);
        }
        return new RegisterWorkerResponse(account.getAccountUuid(), account.getLoginId(), account.getName());
    }
}
