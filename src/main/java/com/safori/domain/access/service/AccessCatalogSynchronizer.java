package com.safori.domain.access.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 기동 시 코드 카탈로그({@code PermissionCode}, {@code RoleTemplateCode})를 DB에 맞춘다. enum만 고쳐 배포해도
 * 새 권한 행이 바로 생겨서 운영자가 부여·회수할 수 있다. 이어서 옛 버전 템플릿으로 만든 기관 역할에 그 뒤 버전에서
 * 추가된 기본 권한을 넣는다({@link AccessProvisioningDomainService#upgradeTemplateRoles}).
 *
 * <p>컨테이너 여러 개가 동시에 넣다 겹치면 한쪽 트랜잭션이 통째로 롤백되므로 한 번 다시 돌린다.
 * 다시 돌 때는 다른 컨테이너가 넣은 행을 읽기만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessCatalogSynchronizer implements ApplicationRunner {

    private final AccessProvisioningDomainService provisioningService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            synchronize();
        } catch (DataIntegrityViolationException e) {
            log.info("권한 카탈로그를 다른 컨테이너가 먼저 동기화했다 — 다시 맞춘다");
            synchronize();
        }
    }

    private void synchronize() {
        int upgraded = provisioningService.upgradeTemplateRoles();
        if (upgraded > 0) {
            log.info("옛 버전 템플릿으로 만든 기관 역할 {}개에 새 기본 권한을 넣었다", upgraded);
        }
    }
}
