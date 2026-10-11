package com.safori.api.operator.service;

import com.safori.api.operator.dto.OrganizationRolePermissionsResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.repository.AccessRoleRepository;
import com.safori.domain.access.service.AccessRoleDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import static com.safori.domain.access.exception.AccessHandler.ROLE_NOT_FOUND;
import static com.safori.domain.organization.exception.OrganizationHandler.NOT_FOUND;

/** Existing organization roles retain their custom permissions until an operator changes them explicitly. */
@UseCase
@RequiredArgsConstructor
public class OperatorRolePermissionsUseCase {

    private final OrganizationRepository organizationRepository;
    private final AccessRoleRepository roleRepository;
    private final AccessRoleDomainService roleDomainService;

    @Transactional(readOnly = true)
    public OrganizationRolePermissionsResponse get(String organizationPublicId, String roleCode) {
        return response(findRole(organizationPublicId, roleCode));
    }

    @Transactional
    public OrganizationRolePermissionsResponse grant(String organizationPublicId, String roleCode,
                                                     PermissionCode permission) {
        AccessRole role = findRole(organizationPublicId, roleCode);
        roleDomainService.grantPermission(role, permission);
        return response(role);
    }

    @Transactional
    public OrganizationRolePermissionsResponse revoke(String organizationPublicId, String roleCode,
                                                      PermissionCode permission) {
        AccessRole role = findRole(organizationPublicId, roleCode);
        roleDomainService.revokePermission(role, permission);
        return response(role);
    }

    private AccessRole findRole(String organizationPublicId, String roleCode) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(() -> NOT_FOUND);
        return roleRepository.findByOrganizationAndCode(organization, roleCode)
                .orElseThrow(() -> ROLE_NOT_FOUND);
    }

    private OrganizationRolePermissionsResponse response(AccessRole role) {
        return new OrganizationRolePermissionsResponse(role.getCode(), role.getDataScope(),
                roleDomainService.getPermissions(role).stream().sorted().toList());
    }
}
