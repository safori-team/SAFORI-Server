package com.safori.api.operator.service;

import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.dto.CreateOrganizationResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * SAFORI 운영자가 기관과 최초 기관 관리자를 한 번에 만든다.
 *
 * <p>기관 안에 승인할 사람이 아직 없으므로 초대·승인 actor를 null(운영 처리)로 두고 바로 ACTIVE로 만든다.
 * 한 트랜잭션이라 중간에 실패(로그인 아이디 중복 등)하면 기관도 남지 않는다.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class CreateOrganizationUseCase {

    private final OrganizationDomainService organizationDomainService;
    private final BackofficeAccountDomainService accountDomainService;
    private final OrganizationMemberDomainService memberDomainService;

    @Transactional
    public CreateOrganizationResponse execute(CreateOrganizationRequest request) {
        Organization organization = organizationDomainService.create(request.getOrganizationName());
        BackofficeAccount admin = accountDomainService.register(
                request.getAdminLoginId(), request.getAdminPassword(), request.getAdminName(),
                request.getAdminPhone());
        OrganizationMember member = memberDomainService.invite(organization, admin, RoleTemplateCode.ORG_ADMIN, null);
        memberDomainService.approve(member, null);

        log.info("운영자 기관 생성: organization={}, adminAccount={}",
                organization.getPublicId(), admin.getAccountUuid());
        return new CreateOrganizationResponse(organization.getPublicId(), admin.getAccountUuid());
    }
}
