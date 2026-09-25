package com.safori.security;

import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.GUARDIAN;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;

/**
 * 백오피스 보안 통합 테스트({@code @SpringBootTest})용 데이터를 실제 도메인 서비스로만 만든다 —
 * 기관 생성(기본 역할·그룹), 초대 → 승인, 배정, 보호자 연결. 저장소에 직접 넣지 않으므로 서비스의
 * 기관 경계·상태 규칙도 함께 거친다.
 */
@RequiredArgsConstructor
public class BackofficeFixture {

    public static final String PASSWORD = "password1234!";

    private final OrganizationDomainService organizationService;
    private final BackofficeAccountDomainService accountService;
    private final OrganizationMemberDomainService memberService;
    private final CareRelationDomainService careRelationService;

    private final AtomicInteger sequence = new AtomicInteger();

    public Organization organization() {
        return organizationService.create("기관-" + sequence.incrementAndGet());
    }

    public BackofficeAccount account() {
        return accountService.register("login-" + sequence.incrementAndGet(), PASSWORD, "구성원", null);
    }

    /** 기본 역할 그룹에 초대만 된(승인 전) 구성원. */
    public OrganizationMember pendingMember(Organization organization, RoleTemplateCode role) {
        return memberService.invite(organization, account(), role, null);
    }

    /** 기본 역할 그룹에 초대하고 SAFORI 운영 승인까지 마친 구성원. */
    public OrganizationMember activeMember(Organization organization, RoleTemplateCode role) {
        return memberService.approve(pendingMember(organization, role), null);
    }

    public CareRecipient recipient(Organization organization) {
        return careRelationService.registerRecipient(organization, null);
    }

    /**
     * 권한표 검증 기본 시나리오.
     * <pre>
     * 기관 A  관리자
     *         담당자 ──배정── 어르신(recipient) ──연결── 보호자
     *         다른 담당자 ──배정── 다른 어르신(otherRecipient) ──연결── 다른 보호자
     * 기관 B  관리자, 어르신(foreignRecipient)
     * </pre>
     */
    public Scenario scenario() {
        Organization organization = organization();
        OrganizationMember admin = activeMember(organization, ORG_ADMIN);
        OrganizationMember worker = activeMember(organization, CARE_WORKER);
        OrganizationMember guardian = activeMember(organization, GUARDIAN);
        OrganizationMember otherWorker = activeMember(organization, CARE_WORKER);
        OrganizationMember otherGuardian = activeMember(organization, GUARDIAN);

        CareRecipient recipient = recipient(organization);
        careRelationService.assignWorker(recipient, worker, admin, "최초 배정");
        careRelationService.linkGuardian(recipient, guardian, admin);

        CareRecipient otherRecipient = recipient(organization);
        careRelationService.assignWorker(otherRecipient, otherWorker, admin, "최초 배정");
        careRelationService.linkGuardian(otherRecipient, otherGuardian, admin);

        Organization foreignOrganization = organization();
        OrganizationMember foreignAdmin = activeMember(foreignOrganization, ORG_ADMIN);
        CareRecipient foreignRecipient = recipient(foreignOrganization);

        return new Scenario(organization, admin, worker, guardian, otherWorker, otherGuardian,
                recipient, otherRecipient, foreignOrganization, foreignAdmin, foreignRecipient);
    }

    public record Scenario(Organization organization,
                           OrganizationMember admin,
                           OrganizationMember worker,
                           OrganizationMember guardian,
                           OrganizationMember otherWorker,
                           OrganizationMember otherGuardian,
                           /* worker 배정 + guardian 연결 */
                           CareRecipient recipient,
                           /* 같은 기관, otherWorker 배정 + otherGuardian 연결 */
                           CareRecipient otherRecipient,
                           Organization foreignOrganization,
                           OrganizationMember foreignAdmin,
                           CareRecipient foreignRecipient) {

        /** 기관 A에서 해당 기본 역할을 가진 구성원(recipient와 관계가 있는 쪽). */
        public OrganizationMember memberOf(RoleTemplateCode role) {
            return switch (role) {
                case ORG_ADMIN -> admin;
                case CARE_WORKER -> worker;
                case GUARDIAN -> guardian;
            };
        }
    }
}
