package com.safori.domain.care.service;

import com.safori.common.config.PasswordConfig;
import com.safori.domain.access.adaptor.AccessGrantAdaptorImpl;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.EffectivePermissionResolver;
import com.safori.domain.access.service.AccessGroupDomainServiceImpl;
import com.safori.domain.access.service.AccessProvisioningDomainServiceImpl;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.account.service.BackofficeAccountDomainServiceImpl;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationDomainServiceImpl;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 MySQL에서 동시 재배정 후에도 현재 담당자가 한 명인지 수동 검증한다.
 *
 * <p>MySQL 기본 격리수준(REPEATABLE READ)에서는 호출자 트랜잭션이 어르신 행을 잠그기 전에 무언가를 읽었다면,
 * 잠금을 얻은 뒤의 일반 조회도 그때의 스냅샷을 본다. 현재 배정을 일반 조회로 읽으면 먼저 끝난 재배정이 보이지 않아
 * 현재 담당자가 둘이 된다. H2는 READ COMMITTED라 이 경합이 드러나지 않아 MySQL로 따로 확인한다.
 * 환경변수가 없으면 자동 skip 되므로 CI(`./gradlew test`)에는 영향 없다.
 *
 * <p>실행 예 (검증용 빈 스키마에 safori.sql을 먼저 적용한다. 실행할 때마다 검증 데이터가 쌓인다):
 * <pre>
 * mysql -e "create database safori_verify"
 * mysql safori_verify &lt; src/main/resources/db/safori.sql
 * TEST_MYSQL_URL="jdbc:mysql://127.0.0.1:3306/safori_verify" TEST_MYSQL_USERNAME=root TEST_MYSQL_PASSWORD= \
 *   ./gradlew test --tests '*CareRelationConcurrencyManualVerifyTest*' -i
 * </pre>
 */
@DataJpaTest(properties = {
        "spring.datasource.url=${TEST_MYSQL_URL}",
        "spring.datasource.username=${TEST_MYSQL_USERNAME:root}",
        "spring.datasource.password=${TEST_MYSQL_PASSWORD:}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        OrganizationDomainServiceImpl.class,
        OrganizationMemberDomainServiceImpl.class,
        BackofficeAccountDomainServiceImpl.class,
        AccessProvisioningDomainServiceImpl.class,
        AccessGroupDomainServiceImpl.class,
        CareRelationDomainServiceImpl.class,
        EffectivePermissionResolver.class,
        AccessGrantAdaptorImpl.class,
        PasswordConfig.class
})
@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = ".+")
class CareRelationConcurrencyManualVerifyTest {

    @Autowired OrganizationDomainService organizationService;
    @Autowired OrganizationMemberDomainService memberService;
    @Autowired BackofficeAccountDomainService accountService;
    @Autowired CareRelationDomainService careRelationService;
    @Autowired CareRecipientRepository recipientRepository;
    @Autowired CareAssignmentRepository assignmentRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @Test
    @DisplayName("두 관리자가 같은 어르신을 동시에 재배정해도 현재 담당자는 한 명이다")
    void concurrentReassignmentKeepsSingleCurrentWorker() throws Exception {
        Organization organization = organizationService.create("동시성 검증 " + runId);
        OrganizationMember admin = activeMember(organization, ORG_ADMIN, "admin");
        OrganizationMember initialWorker = activeMember(organization, CARE_WORKER, "initial");
        OrganizationMember firstWorker = activeMember(organization, CARE_WORKER, "first");
        OrganizationMember secondWorker = activeMember(organization, CARE_WORKER, "second");
        CareRecipient recipient = careRelationService.registerRecipient(organization, null);
        careRelationService.assignWorker(recipient, initialWorker, admin, "최초 배정");

        CountDownLatch snapshotsTaken = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> reassignInCallerTransaction(recipient, firstWorker, admin, snapshotsTaken));
            Future<?> second = pool.submit(() -> reassignInCallerTransaction(recipient, secondWorker, admin, snapshotsTaken));
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        Integer currentAssignments = new TransactionTemplate(transactionManager).execute(status ->
                assignmentRepository.findCurrentByRecipientForUpdate(recipient).size());
        assertThat(currentAssignments).isEqualTo(1);
    }

    /** UseCase처럼 호출자 트랜잭션이 먼저 어르신을 조회(스냅샷 확정)한 뒤 배정을 바꾼다. */
    private void reassignInCallerTransaction(CareRecipient recipient, OrganizationMember worker,
                                             OrganizationMember admin, CountDownLatch snapshotsTaken) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recipientRepository.findById(recipient.getId()).orElseThrow();
            snapshotsTaken.countDown();
            awaitQuietly(snapshotsTaken);
            careRelationService.assignWorker(recipient, worker, admin, "동시 재배정");
        });
    }

    private OrganizationMember activeMember(Organization organization, RoleTemplateCode role, String name) {
        OrganizationMember member = memberService.invite(organization,
                accountService.register(name + "-" + runId, "password1234!", name, null), role, null);
        return memberService.approve(member, null);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
