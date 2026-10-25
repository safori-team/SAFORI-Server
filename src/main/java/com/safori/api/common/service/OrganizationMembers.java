package com.safori.api.common.service;

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
 * 경로의 계정 UUID를 요청한 구성원 기관의 담당자·보호자로 찾는다. 다른 기관 구성원이거나 그 역할이
 * 아니면 없는 구성원으로 본다(4307) — 다른 기관에 그 계정이 있는지도 드러내지 않는다.
 *
 * <p>호출자가 이 API를 쓸 수 있는지(권한)는 보안 규칙이 먼저 판정한다. 여기서는 경로의 대상만 확인한다.
 */
@Component
@RequiredArgsConstructor
public class OrganizationMembers {

    private final BackofficeAccountRepository accountRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessGroupDomainService accessGroupDomainService;

    /** 경로의 {@code managerId}로 담당자를 찾는다. */
    public OrganizationMember worker(BackofficeActor actor, String managerId) {
        return get(actor, managerId, RoleTemplateCode.CARE_WORKER);
    }

    /** 경로의 {@code guardianId}로 보호자를 찾는다. */
    public OrganizationMember guardian(BackofficeActor actor, String guardianId) {
        return get(actor, guardianId, RoleTemplateCode.GUARDIAN);
    }

    /** 요청한 구성원 자신(배정·연결·기록의 행위자). */
    public OrganizationMember actorOf(BackofficeActor actor) {
        return memberRepository.getReferenceById(actor.organizationMemberId());
    }

    private OrganizationMember get(BackofficeActor actor, String accountUuid, RoleTemplateCode template) {
        return accountRepository.findByAccountUuid(accountUuid)
                .flatMap(memberRepository::findCurrentByAccount)
                .filter(member -> member.getOrganization().getId().equals(actor.organizationId()))
                .filter(member -> accessGroupDomainService.primaryTemplateOf(member)
                        .filter(template::equals).isPresent())
                .orElseThrow(() -> OrganizationHandler.MEMBER_NOT_FOUND);
    }
}
