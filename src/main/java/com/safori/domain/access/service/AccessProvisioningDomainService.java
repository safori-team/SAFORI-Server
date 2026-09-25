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
     * 기관에 기본 역할·그룹을 만든다. 템플릿마다 기관 소유 역할과 같은 이름의 기본 그룹을 만들고 둘을 연결한다.
     *
     * <p>이미 있는 역할·그룹은 건드리지 않는다. 템플릿이 새로 추가되면 기존 기관에 다시 호출해 빠진 기본 역할만
     * 만들 수 있고, 기관이 바꾼 권한 구성은 덮어쓰지 않는다.
     */
    void provisionDefaults(Organization organization);
}
