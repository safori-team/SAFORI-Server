package com.safori.domain;

import com.safori.common.config.PasswordConfig;
import com.safori.domain.access.adaptor.AccessGrantAdaptorImpl;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.EffectivePermissionResolver;
import com.safori.domain.access.service.AccessGroupDomainServiceImpl;
import com.safori.domain.access.service.AccessProvisioningDomainServiceImpl;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.account.service.BackofficeAccountDomainServiceImpl;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.care.service.CareRelationDomainServiceImpl;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationDomainServiceImpl;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서비스 호출마다 트랜잭션이 따로 열릴 때(스케줄러, OSIV 없는 호출 등) 상태 변경이 저장되는지 본다.
 *
 * <p>호출자가 다른 트랜잭션에서 읽은(준영속) 엔티티를 넘기면, 그 인스턴스를 바꿔도 저장되지 않는다. 도메인 서비스는
 * 대상을 자기 트랜잭션에서 다시 읽어 바꾸고 판단해야 한다. Mockito 단위 테스트로는 영속성 컨텍스트 경계를 확인할 수
 * 없어 실제 JPA로 확인한다. 테스트 트랜잭션을 끄므로 데이터는 이 테스트 컨텍스트 전용 임베디드 DB에 커밋된다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        BackofficeAccountDomainServiceImpl.class,
        OrganizationDomainServiceImpl.class,
        OrganizationMemberDomainServiceImpl.class,
        AccessProvisioningDomainServiceImpl.class,
        AccessGroupDomainServiceImpl.class,
        CareRelationDomainServiceImpl.class,
        EffectivePermissionResolver.class,
        AccessGrantAdaptorImpl.class,
        PasswordConfig.class
})
class BackofficeServiceTransactionTest {

    @Autowired BackofficeAccountDomainService accountService;
    @Autowired OrganizationDomainService organizationService;
    @Autowired OrganizationMemberDomainService memberService;
    @Autowired CareRelationDomainService careRelationService;
    @Autowired BackofficeAccountRepository accountRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired OrganizationMemberRepository memberRepository;
    @Autowired CareAssignmentRepository assignmentRepository;

    @Test
    @DisplayName("계정 정지·강제 로그아웃: 다른 트랜잭션에서 읽은 계정을 넘겨도 저장된다")
    void accountChangesPersist() {
        BackofficeAccount account = accountService.register(uniqueLoginId(), "password1234!", "관리자");

        accountService.revokeIssuedTokens(account);
        accountService.suspend(account);

        BackofficeAccount stored = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(BackofficeAccountStatus.SUSPENDED);
        assertThat(stored.getAuthVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("기관 비활성화: 다른 트랜잭션에서 읽은 기관을 넘겨도 저장된다")
    void organizationDeactivationPersists() {
        Organization organization = organizationService.create("사포리 복지관");

        organizationService.deactivate(organization);

        assertThat(organizationRepository.findById(organization.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("가입 승인·정지·소속 종료: 다른 트랜잭션에서 읽은 구성원을 넘겨도 저장된다")
    void membershipChangesPersist() {
        Organization organization = organizationService.create("사포리 복지관");
        OrganizationMember member = memberService.invite(organization,
                accountService.register(uniqueLoginId(), "password1234!", "담당자"), CARE_WORKER, null);

        memberService.approve(member, null);
        assertThat(statusOf(member)).isEqualTo(OrganizationMemberStatus.ACTIVE);

        memberService.suspend(member);
        assertThat(statusOf(member)).isEqualTo(OrganizationMemberStatus.SUSPENDED);

        memberService.revoke(member, null);
        assertThat(statusOf(member)).isEqualTo(OrganizationMemberStatus.REVOKED);
    }

    @Test
    @DisplayName("담당자 배정: 다른 트랜잭션에서 읽어 연관 엔티티가 초기화되지 않은 담당자를 넘겨도 배정된다")
    void assignWorkerAcceptsDetachedWorker() {
        Organization organization = organizationService.create("사포리 복지관");
        OrganizationMember worker = activeMember(organization, CARE_WORKER);
        OrganizationMember detachedWorker = memberRepository.findById(worker.getId()).orElseThrow();
        CareRecipient recipient = careRelationService.registerRecipient(organization, null);

        careRelationService.assignWorker(recipient, detachedWorker, null, "최초 배정");

        assertThat(assignmentRepository.existsByRecipientAndWorkerAndEndedAtIsNull(recipient, worker)).isTrue();
    }

    @Test
    @DisplayName("담당자 배정: 넘겨받은 인스턴스가 옛 상태여도 현재 상태로 판단한다 — 그사이 정지된 담당자는 거부")
    void assignWorkerUsesCurrentState() {
        Organization organization = organizationService.create("사포리 복지관");
        OrganizationMember worker = activeMember(organization, CARE_WORKER);
        CareRecipient recipient = careRelationService.registerRecipient(organization, null);

        memberService.suspend(worker);

        assertThatThrownBy(() -> careRelationService.assignWorker(recipient, worker, null, null))
                .isEqualTo(CareHandler.WORKER_NOT_ASSIGNABLE);
    }

    private OrganizationMember activeMember(Organization organization, RoleTemplateCode role) {
        OrganizationMember invited = memberService.invite(organization,
                accountService.register(uniqueLoginId(), "password1234!", "구성원"), role, null);
        return memberService.approve(invited, null);
    }

    private OrganizationMemberStatus statusOf(OrganizationMember member) {
        return memberRepository.findById(member.getId()).orElseThrow().getStatus();
    }

    private static String uniqueLoginId() {
        return "user-" + UUID.randomUUID();
    }
}
