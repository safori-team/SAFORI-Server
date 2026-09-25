package com.safori.domain.access.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.entity.AccessMemberRole;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.repository.AccessMemberRoleRepository;
import com.safori.domain.access.repository.AccessPermissionRepository;
import com.safori.domain.access.repository.AccessRolePermissionRepository;
import com.safori.domain.access.repository.AccessRoleRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.safori.domain.access.exception.AccessHandler.INVALID_ROLE_EXPIRY;
import static com.safori.domain.access.exception.AccessHandler.PERMISSION_NOT_ASSIGNABLE;
import static com.safori.domain.access.exception.AccessHandler.ROLE_CODE_ALREADY_EXISTS;

@Transactional
@DomainService
@RequiredArgsConstructor
public class AccessRoleDomainServiceImpl implements AccessRoleDomainService {

    private final AccessRoleRepository roleRepository;
    private final AccessPermissionRepository permissionRepository;
    private final AccessRolePermissionRepository rolePermissionRepository;
    private final AccessMemberRoleRepository memberRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public AccessRole getTemplateRole(Organization organization, RoleTemplateCode template) {
        return roleRepository.findByOrganizationAndCode(organization, template.name())
                .orElseThrow(() -> new IllegalStateException("기본 역할이 준비되지 않은 기관입니다: " + template));
    }

    @Override
    public AccessRole createCustomRole(Organization organization, String code, String name, DataScope dataScope,
                                       Set<PermissionCode> permissions) {
        if (roleRepository.existsByOrganizationAndCode(organization, code)) {
            throw ROLE_CODE_ALREADY_EXISTS;
        }
        List<AccessPermission> assignable = permissions.stream().map(this::findAssignable).toList();

        AccessRole role = roleRepository.save(AccessRole.custom(organization, code, name, dataScope));
        assignable.forEach(permission -> rolePermissionRepository.save(AccessRolePermission.of(role, permission)));
        return role;
    }

    @Override
    public void grantPermission(AccessRole role, PermissionCode code) {
        AccessPermission permission = findAssignable(code);
        if (!rolePermissionRepository.existsByRoleAndPermission(role, permission)) {
            rolePermissionRepository.save(AccessRolePermission.of(role, permission));
        }
    }

    @Override
    public void revokePermission(AccessRole role, PermissionCode code) {
        permissionRepository.findByCode(code.name())
                .flatMap(permission -> rolePermissionRepository.findByRoleAndPermission(role, permission))
                .ifPresent(rolePermissionRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<PermissionCode> getPermissions(AccessRole role) {
        Set<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        rolePermissionRepository.findPermissionCodesByRole(role)
                .forEach(code -> PermissionCode.find(code).ifPresent(permissions::add));
        return permissions;
    }

    @Override
    public AccessMemberRole grantMemberRole(OrganizationMember member, AccessRole role, OrganizationMember grantedBy,
                                            LocalDateTime expiresAt, String reason) {
        Organization.requireMembers(role.getOrganization(), member, grantedBy);
        LocalDateTime now = LocalDateTime.now();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw INVALID_ROLE_EXPIRY;
        }

        Optional<AccessMemberRole> existing = memberRoleRepository.findByMemberAndRole(member, role);
        if (existing.isPresent()) {
            existing.get().regrant(grantedBy, expiresAt, reason, now);
            return existing.get();
        }
        return memberRoleRepository.save(AccessMemberRole.grant(member, role, grantedBy, expiresAt, reason, now));
    }

    @Override
    public void revokeMemberRole(OrganizationMember member, AccessRole role) {
        memberRoleRepository.findByMemberAndRole(member, role)
                .ifPresent(grant -> grant.revoke(LocalDateTime.now()));
    }

    /** 원문 열람처럼 기관이 부여할 수 없는 권한은 기본 역할이든 직접 만든 역할이든 넣지 못한다. */
    private AccessPermission findAssignable(PermissionCode code) {
        AccessPermission permission = permissionRepository.findByCode(code.name())
                .orElseThrow(() -> new IllegalStateException("권한 카탈로그가 동기화되지 않았습니다: " + code));
        if (!permission.isOrganizationAssignable()) {
            throw PERMISSION_NOT_ASSIGNABLE;
        }
        return permission;
    }
}
