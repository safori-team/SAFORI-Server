package com.safori.domain.access.adaptor;

import com.safori.domain.access.policy.PermissionGrant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구성원이 역할을 통해 받은 권한. 최종 권한 = 그룹 상속분 ∪ 개인 역할분.
 */
public interface AccessGrantAdaptor {

    /** 소속 그룹을 통해 상속받은 권한. 그룹·역할이 구성원과 같은 기관이고 활성인 것만. */
    List<PermissionGrant> queryGrantsInheritedFromGroups(Long memberId, Long organizationId);

    /** 직접 부여된 역할의 권한. {@code now} 시점에 회수·만료되지 않았고 같은 기관의 활성 역할인 것만. */
    List<PermissionGrant> queryGrantsFromDirectRoles(Long memberId, Long organizationId, LocalDateTime now);
}
