package com.safori.domain.access.service;

import com.safori.domain.access.entity.AccessMemberRole;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 기관 역할의 권한 구성과 개인 예외 역할을 관리한다.
 *
 * <p>호출자의 권한(예: 이 관리자가 역할을 바꿀 수 있는가)은 검사하지 않는다. 그 판정은 UseCase 진입점의
 * {@code BackofficeAccessPolicy}가 맡고, 여기서는 기관 경계와 부여 가능 여부 같은 데이터 규칙만 지킨다.
 */
public interface AccessRoleDomainService {

    /** 기관 생성 시 기본 템플릿에서 만들어진 기관 역할. */
    AccessRole getTemplateRole(Organization organization, RoleTemplateCode template);

    /** 기관 전용 역할을 만든다. 기관에서 부여할 수 없는 권한이 섞여 있으면 아무것도 만들지 않는다. */
    AccessRole createCustomRole(Organization organization, String code, String name, DataScope dataScope,
                                Set<PermissionCode> permissions);

    /** 기관 역할에 권한을 추가한다. 이미 있으면 그대로 둔다. */
    void grantPermission(AccessRole role, PermissionCode permission);

    void revokePermission(AccessRole role, PermissionCode permission);

    Set<PermissionCode> getPermissions(AccessRole role);

    /**
     * 개인 예외 역할을 부여한다. 회수·만료된 같은 역할이 있으면 다시 유효하게 만든다.
     *
     * @param grantedBy null이면 SAFORI 운영(시스템) 부여
     * @param expiresAt null이면 회수 전까지 유효
     */
    AccessMemberRole grantMemberRole(OrganizationMember member, AccessRole role, OrganizationMember grantedBy,
                                     LocalDateTime expiresAt, String reason);

    void revokeMemberRole(OrganizationMember member, AccessRole role);
}
