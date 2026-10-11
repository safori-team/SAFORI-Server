package com.safori.domain.access.service;

import com.safori.domain.access.model.AccessCatalog;
import com.safori.domain.organization.entity.Organization;

public interface AccessProvisioningDomainService {

    /**
     * 코드 카탈로그({@code PermissionCode}, {@code RoleTemplateCode})를 DB에 멱등 동기화한다.
     * 권한은 코드 기준 upsert, 역할 템플릿은 (코드, 버전)이 없을 때만 추가한다.
     */
    AccessCatalog synchronizeCatalog();

    /**
     * 카탈로그를 동기화한 뒤, 옛 버전 템플릿으로 만들어진 기관 역할을 현재 버전으로 올린다.
     *
     * <p>역할의 생성 기준 버전 뒤 revision에서 추가된 기본 권한만 넣고(이미 있으면 건너뜀), 기관이 바꾼 구성은
     * 건드리지 않는다 — 기관이 직접 뺀 옛 권한은 되살리지 않는다. 생성 기준을 현재 버전으로 옮겨 두므로 같은
     * 버전에 대해 다시 불러도 아무것도 하지 않는다.
     *
     * @return 올린 역할 수
     */
    int upgradeTemplateRoles();

    /**
     * 기관에 기본 역할·그룹을 만든다. 템플릿마다 기관 소유 역할과 같은 이름의 기본 그룹을 만들고 둘을 연결한다.
     *
     * <p>이미 있는 역할·그룹은 건드리지 않는다. 템플릿이 새로 추가되면 기존 기관에 다시 호출해 빠진 기본 역할만
     * 만들 수 있고, 기관이 바꾼 권한 구성은 덮어쓰지 않는다.
     */
    void provisionDefaults(Organization organization);
}
