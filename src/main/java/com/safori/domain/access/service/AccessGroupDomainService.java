package com.safori.domain.access.service;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;

/**
 * 그룹 소속과 그룹-역할 연결을 관리한다. 그룹 구성원은 그룹에 연결된 역할을 상속한다.
 * 호출자 권한은 UseCase의 {@code BackofficeAccessPolicy}가 검사한다.
 */
import java.util.Optional;

public interface AccessGroupDomainService {

    /** 기관 생성 시 기본 템플릿마다 만들어진 기본 그룹. */
    AccessGroup getSystemGroup(Organization organization, RoleTemplateCode template);

    AccessGroup createCustomGroup(Organization organization, String name);

    /** 그룹에 구성원을 넣는다. 이미 있으면 그대로 둔다. 그룹과 구성원은 같은 기관이어야 한다. */
    void addMember(AccessGroup group, OrganizationMember member);

    void removeMember(AccessGroup group, OrganizationMember member);

    /** 소속 종료(REVOKED)되지 않은 구성원이 그룹에 있는지. 대기·정지 구성원도 포함한다. */
    boolean hasCurrentMember(AccessGroup group);

    /** 구성원이 속한 기본 그룹의 역할 템플릿. 여러 개면 {@link RoleTemplateCode} 선언 순서(관리자 우선)로 하나를 고른다. */
    Optional<RoleTemplateCode> primaryTemplateOf(OrganizationMember member);

    /** 그룹에 역할을 연결한다. 이미 있으면 그대로 둔다. 그룹과 역할은 같은 기관이어야 한다. */
    void assignRole(AccessGroup group, AccessRole role);

    void unassignRole(AccessGroup group, AccessRole role);
}
