package com.safori.domain.organization.service;

import com.safori.domain.organization.entity.Organization;

/**
 * 상태를 바꾸는 메서드는 인자로 받은 기관을 식별자로만 쓰고, 이 트랜잭션에서 다시 읽은 기관을 바꿔 돌려준다.
 */
public interface OrganizationDomainService {

    /** 기관을 만들고 기본 역할(기관 관리자·담당자·보호자)과 같은 이름의 기본 그룹을 함께 준비한다. */
    Organization create(String name);

    /** 기관 비활성화. 관리자를 포함한 모든 소속 구성원의 권한 판정이 다음 요청부터 거부된다. */
    Organization deactivate(Organization organization);

    Organization activate(Organization organization);
}
