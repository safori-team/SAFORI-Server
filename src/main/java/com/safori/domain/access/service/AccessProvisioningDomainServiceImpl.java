package com.safori.domain.access.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupRole;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.entity.AccessRoleTemplate;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.model.AccessCatalog;
import com.safori.domain.access.repository.AccessGroupRepository;
import com.safori.domain.access.repository.AccessGroupRoleRepository;
import com.safori.domain.access.repository.AccessPermissionRepository;
import com.safori.domain.access.repository.AccessRolePermissionRepository;
import com.safori.domain.access.repository.AccessRoleRepository;
import com.safori.domain.access.repository.AccessRoleTemplateRepository;
import com.safori.domain.organization.entity.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Transactional
@DomainService
@RequiredArgsConstructor
public class AccessProvisioningDomainServiceImpl implements AccessProvisioningDomainService {

    private final AccessPermissionRepository permissionRepository;
    private final AccessRoleTemplateRepository templateRepository;
    private final AccessRoleRepository roleRepository;
    private final AccessRolePermissionRepository rolePermissionRepository;
    private final AccessGroupRepository groupRepository;
    private final AccessGroupRoleRepository groupRoleRepository;

    @Override
    public AccessCatalog synchronizeCatalog() {
        Map<PermissionCode, AccessPermission> permissions = new EnumMap<>(PermissionCode.class);
        for (PermissionCode code : PermissionCode.values()) {
            AccessPermission permission = permissionRepository.findByCode(code.name())
                    .orElseGet(() -> permissionRepository.save(AccessPermission.from(code)));
            permission.syncWith(code);
            permissions.put(code, permission);
        }

        Map<RoleTemplateCode, AccessRoleTemplate> templates = new EnumMap<>(RoleTemplateCode.class);
        for (RoleTemplateCode code : RoleTemplateCode.values()) {
            AccessRoleTemplate template = templateRepository.findByCodeAndVersion(code.name(), code.getVersion())
                    .orElseGet(() -> templateRepository.save(AccessRoleTemplate.from(code)));
            templates.put(code, template);
        }
        return new AccessCatalog(permissions, templates);
    }

    @Override
    public void provisionDefaults(Organization organization) {
        AccessCatalog catalog = synchronizeCatalog();
        for (RoleTemplateCode template : RoleTemplateCode.values()) {
            AccessRole role = roleRepository.findByOrganizationAndCode(organization, template.name())
                    .orElseGet(() -> createTemplateRole(organization, template, catalog));
            AccessGroup group = groupRepository.findByOrganizationAndSystemCode(organization, template.name())
                    .orElseGet(() -> groupRepository.save(AccessGroup.system(organization, template)));
            if (!groupRoleRepository.existsByGroupAndRole(group, role)) {
                groupRoleRepository.save(AccessGroupRole.of(group, role));
            }
        }
    }

    private AccessRole createTemplateRole(Organization organization, RoleTemplateCode template, AccessCatalog catalog) {
        AccessRole role = roleRepository.save(AccessRole.fromTemplate(organization, catalog.template(template)));
        for (PermissionCode permission : template.getDefaultPermissions()) {
            rolePermissionRepository.save(AccessRolePermission.of(role, catalog.permission(permission)));
        }
        return role;
    }
}
