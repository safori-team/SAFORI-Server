package com.safori.api.operator.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.operator.dto.OrganizationDetailResponse;
import com.safori.api.operator.dto.OrganizationSummaryResponse;
import com.safori.api.operator.dto.ReplaceOrganizationAdminRequest;
import com.safori.api.operator.dto.ReplaceOrganizationAdminResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationStatus;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.model.OrganizationCount;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SAFORI 운영자 콘솔의 기관 목록·상세·이름 변경·상태 변경·관리자 교체. 기관 생성은 {@link CreateOrganizationUseCase}.
 * 관리자·인원 수는 기관 페이지 단위로 한 번에 읽는다(기관마다 조회하지 않는다).
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class OperatorOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CareRecipientRepository recipientRepository;
    private final OrganizationDomainService organizationDomainService;
    private final OrganizationMemberDomainService memberDomainService;
    private final BackofficeAccountDomainService accountDomainService;

    @Transactional(readOnly = true)
    public PagedResponse<OrganizationSummaryResponse> list(String keyword, OrganizationStatus status, int page, int size) {
        Page<Organization> organizations = organizationRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : null, status, PageRequest.of(page - 1, size));
        List<Long> ids = organizations.map(Organization::getId).toList();
        // 빈 IN 절을 만들지 않도록 기관이 없으면 조회하지 않는다.
        Map<Long, OrganizationMember> admins = ids.isEmpty() ? Map.of() : admins(ids);
        Map<Long, Long> workers = ids.isEmpty() ? Map.of()
                : byOrganization(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.CARE_WORKER.name()));
        Map<Long, Long> recipients = ids.isEmpty() ? Map.of()
                : byOrganization(recipientRepository.countActiveByOrganizations(ids));
        return PagedResponse.from(organizations.map(o -> {
            OrganizationMember admin = admins.get(o.getId());
            return new OrganizationSummaryResponse(o.getPublicId(), o.getName(), o.getStatus(),
                    admin == null ? null : new OrganizationSummaryResponse.Admin(
                            admin.getAccount().getLoginId(), admin.getAccount().getName()),
                    workers.getOrDefault(o.getId(), 0L), recipients.getOrDefault(o.getId(), 0L), o.getCreatedDate());
        }));
    }

    @Transactional(readOnly = true)
    public OrganizationDetailResponse get(String organizationPublicId) {
        return detail(organizationOf(organizationPublicId));
    }

    @Transactional
    public void rename(String organizationPublicId, String name) {
        organizationOf(organizationPublicId).rename(name.trim());
    }

    /** 비활성화하면 관리자를 포함한 소속 구성원 전원이 다음 요청부터 권한을 잃는다. 다시 활성화하면 돌아온다. */
    @Transactional
    public void changeStatus(String organizationPublicId, OrganizationStatus status) {
        Organization organization = organizationOf(organizationPublicId);
        if (status == OrganizationStatus.ACTIVE) {
            organizationDomainService.activate(organization);
        } else {
            organizationDomainService.deactivate(organization);
        }
    }

    /**
     * 관리자 교체. 기존 관리자는 소속을 종료하고 계정을 정지해 발급된 토큰도 끊는다(계정은 한 기관에만 소속되므로
     * 다시 쓰지 않는다). 새 관리자 계정을 만들어 기관 생성 때와 같이 바로 ACTIVE 관리자로 둔다.
     * 기관 행을 잠가 동시 교체를 직렬화하고, 새 아이디가 중복이면 기존 관리자도 그대로 남는다(한 트랜잭션).
     */
    @Transactional
    public ReplaceOrganizationAdminResponse replaceAdmin(String organizationPublicId,
                                                         ReplaceOrganizationAdminRequest request) {
        Organization organization = organizationRepository.findByIdForUpdate(organizationOf(organizationPublicId).getId())
                .orElseThrow(() -> OrganizationHandler.NOT_FOUND);
        // 새 계정을 먼저 만들어 아이디 중복이면 기존 관리자를 건드리기 전에 실패한다.
        BackofficeAccount admin = accountDomainService.register(request.adminLoginId(), request.adminPassword(),
                request.adminName(), request.adminPhone());
        for (OrganizationMember current : memberRepository.findCurrentByTemplate(
                List.of(organization.getId()), RoleTemplateCode.ORG_ADMIN.name())) {
            memberDomainService.revoke(current, null);
            accountDomainService.suspend(current.getAccount());
        }
        OrganizationMember member = memberDomainService.invite(organization, admin, RoleTemplateCode.ORG_ADMIN, null);
        memberDomainService.approve(member, null);

        log.info("운영자 기관 관리자 교체: organization={}, adminAccount={}",
                organization.getPublicId(), admin.getAccountUuid());
        return new ReplaceOrganizationAdminResponse(admin.getAccountUuid());
    }

    private OrganizationDetailResponse detail(Organization organization) {
        List<Long> ids = List.of(organization.getId());
        OrganizationMember admin = admins(ids).get(organization.getId());
        BackofficeAccount account = admin == null ? null : admin.getAccount();
        return new OrganizationDetailResponse(organization.getPublicId(), organization.getName(),
                organization.getStatus(),
                account == null ? null : new OrganizationDetailResponse.Admin(account.getAccountUuid(),
                        account.getLoginId(), account.getName(), account.getPhone(), account.getStatus()),
                count(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.CARE_WORKER.name())),
                count(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.GUARDIAN.name())),
                count(recipientRepository.countActiveByOrganizations(ids)),
                organization.getCreatedDate(), organization.getLastModifiedDate());
    }

    private Organization organizationOf(String organizationPublicId) {
        return organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(() -> OrganizationHandler.NOT_FOUND);
    }

    private Map<Long, OrganizationMember> admins(Collection<Long> organizationIds) {
        return memberRepository.findCurrentByTemplate(organizationIds, RoleTemplateCode.ORG_ADMIN.name()).stream()
                .collect(Collectors.toMap(m -> m.getOrganization().getId(), Function.identity(), (a, b) -> a));
    }

    private static Map<Long, Long> byOrganization(List<OrganizationCount> counts) {
        return counts.stream().collect(Collectors.toMap(OrganizationCount::organizationId, OrganizationCount::count));
    }

    private static long count(List<OrganizationCount> counts) {
        return counts.isEmpty() ? 0 : counts.get(0).count();
    }
}
