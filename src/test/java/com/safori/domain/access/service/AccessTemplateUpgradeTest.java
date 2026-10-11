package com.safori.domain.access.service;

import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRoleTemplate;
import com.safori.domain.access.entity.AccessStatus;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.repository.AccessPermissionRepository;
import com.safori.domain.access.repository.AccessRolePermissionRepository;
import com.safori.domain.access.repository.AccessRoleRepository;
import com.safori.domain.access.repository.AccessRoleTemplateRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.service.OrganizationDomainService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_UPDATE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_APPROVE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_WRITE;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 후 기동 시 옛 버전 템플릿으로 만든 기관 역할이 실제 DB에서 어떻게 올라가는지 검증한다.
 */
@SpringBootTest
@Transactional
class AccessTemplateUpgradeTest {

    @Autowired AccessProvisioningDomainService provisioningService;
    @Autowired OrganizationDomainService organizationService;
    @Autowired AccessRoleRepository roleRepository;
    @Autowired AccessRoleTemplateRepository templateRepository;
    @Autowired AccessPermissionRepository permissionRepository;
    @Autowired AccessRolePermissionRepository rolePermissionRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("v2 관리자 역할에는 v3에서 추가된 권한만 넣고, 기관이 뺀 권한은 되살리지 않으며, 다시 돌면 아무것도 안 한다")
    void upgradesOnlyPermissionsAddedAfterRoleVersion() {
        Organization organization = organizationService.create("옛버전복지관");
        AccessRole admin = roleRepository.findByOrganizationAndCode(organization, ORG_ADMIN.name()).orElseThrow();
        rollBackTo(admin, ORG_ADMIN, 2, RECIPIENT_UPDATE);
        revoke(admin, WORK_LOG_WRITE);
        flushAndClear();

        assertThat(provisioningService.upgradeTemplateRoles()).isEqualTo(1);
        flushAndClear();

        AccessRole upgraded = roleRepository.findById(admin.getId()).orElseThrow();
        assertThat(upgraded.getSourceTemplate().getVersion()).isEqualTo(ORG_ADMIN.getVersion());
        assertThat(rolePermissionRepository.findPermissionCodesByRole(upgraded))
                .contains(RECIPIENT_UPDATE.name(), WORK_LOG_HISTORY_APPROVE.name())
                .doesNotContain(WORK_LOG_WRITE.name());

        assertThat(provisioningService.upgradeTemplateRoles()).isZero();
    }

    @Test
    @DisplayName("운영자가 이미 부여한 권한은 다시 넣지 않고 버전만 올린다")
    void skipsPermissionsAlreadyGranted() {
        Organization organization = organizationService.create("수동부여복지관");
        AccessRole admin = roleRepository.findByOrganizationAndCode(organization, ORG_ADMIN.name()).orElseThrow();
        rollBackTo(admin, ORG_ADMIN, 2);
        flushAndClear();

        assertThat(provisioningService.upgradeTemplateRoles()).isEqualTo(1);
        flushAndClear();

        AccessRole upgraded = roleRepository.findById(admin.getId()).orElseThrow();
        assertThat(upgraded.getSourceTemplate().getVersion()).isEqualTo(ORG_ADMIN.getVersion());
        assertThat(rolePermissionRepository.findPermissionCodesByRole(upgraded))
                .containsExactlyInAnyOrderElementsOf(ORG_ADMIN.getDefaultPermissions().stream().map(Enum::name).toList());
    }

    @Test
    @DisplayName("다른 템플릿의 역할에는 그 템플릿의 추가분만 넣는다")
    void upgradesEachTemplateSeparately() {
        Organization organization = organizationService.create("담당자복지관");
        AccessRole worker = roleRepository.findByOrganizationAndCode(organization, CARE_WORKER.name()).orElseThrow();
        rollBackTo(worker, CARE_WORKER, 1, CARE_WORKER.permissionsAddedAfter(1).toArray(PermissionCode[]::new));
        flushAndClear();

        provisioningService.upgradeTemplateRoles();
        flushAndClear();

        AccessRole upgraded = roleRepository.findById(worker.getId()).orElseThrow();
        assertThat(rolePermissionRepository.findPermissionCodesByRole(upgraded))
                .containsExactlyInAnyOrderElementsOf(CARE_WORKER.getDefaultPermissions().stream().map(Enum::name).toList())
                .doesNotContain(RECIPIENT_UPDATE.name());
    }

    /** 배포 전 상태를 흉내 낸다: 역할을 옛 버전 템플릿 기준으로 돌리고, 그 버전에 없던 권한을 뺀다. */
    private void rollBackTo(AccessRole role, RoleTemplateCode template, long version, PermissionCode... notYetAdded) {
        AccessRoleTemplate old = templateRepository.findByCodeAndVersion(template.name(), version)
                .orElseGet(() -> templateRepository.save(AccessRoleTemplate.builder()
                        .code(template.name()).name(template.getDisplayName()).version(version)
                        .dataScope(template.getDataScope()).status(AccessStatus.ACTIVE).build()));
        role.upgradeTemplate(old);
        for (PermissionCode permission : notYetAdded) {
            revoke(role, permission);
        }
    }

    private void revoke(AccessRole role, PermissionCode code) {
        rolePermissionRepository.findByRoleAndPermission(role, permissionRepository.findByCode(code.name()).orElseThrow())
                .ifPresent(rolePermissionRepository::delete);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
