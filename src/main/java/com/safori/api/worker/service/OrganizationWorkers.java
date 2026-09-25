package com.safori.api.worker.service;

import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 경로의 {@code managerId}(계정 UUID)를 요청한 구성원 기관의 담당자로 찾는다. 다른 기관 구성원이거나 담당자가
 * 아니면 없는 구성원으로 본다(4307) — 다른 기관에 그 계정이 있는지도 드러내지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OrganizationWorkers {

    private final BackofficeAccountRepository accountRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessGroupDomainService accessGroupDomainService;

    public OrganizationMember get(BackofficeActor actor, String managerId) {
        return accountRepository.findByAccountUuid(managerId)
                .flatMap(memberRepository::findCurrentByAccount)
                .filter(member -> member.getOrganization().getId().equals(actor.organizationId()))
                .filter(member -> accessGroupDomainService.primaryTemplateOf(member)
                        .filter(RoleTemplateCode.CARE_WORKER::equals).isPresent())
                .orElseThrow(() -> OrganizationHandler.MEMBER_NOT_FOUND);
    }

    /** 요청한 구성원 자신(배정·해제 기록의 행위자). */
    public OrganizationMember actorOf(BackofficeActor actor) {
        return memberRepository.getReferenceById(actor.organizationMemberId());
    }
}
