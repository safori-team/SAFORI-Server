package com.safori.domain.access.policy;

import com.safori.domain.access.adaptor.AccessGrantAdaptor;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 구성원의 최종 권한을 DB의 현재 상태로 계산한다(캐시 없음).
 *
 * <pre>
 *   그룹에서 받은 역할의 권한
 *              +
 *   개인에게 직접 부여한 역할의 권한 (회수·만료 제외)
 *              =
 *   최종 권한
 * </pre>
 *
 * 멤버십·계정·기관 중 하나라도 활성이 아니면 권한이 없다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EffectivePermissionResolver {

    private final AccessGrantAdaptor accessGrantAdaptor;

    public EffectivePermissions resolve(OrganizationMember member) {
        return resolve(member, LocalDateTime.now());
    }

    public EffectivePermissions resolve(OrganizationMember member, LocalDateTime now) {
        if (member == null || !member.isActiveActor()) {
            return EffectivePermissions.none();
        }
        Long memberId = member.getId();
        Long organizationId = member.getOrganization().getId();

        List<PermissionGrant> grants = new ArrayList<>(
                accessGrantAdaptor.queryGrantsInheritedFromGroups(memberId, organizationId));
        grants.addAll(accessGrantAdaptor.queryGrantsFromDirectRoles(memberId, organizationId, now));
        return EffectivePermissions.of(grants);
    }
}
