package com.safori.domain.access.model;

import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRoleTemplate;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;

import java.util.Map;

/**
 * 동기화된 권한·역할 템플릿 행. 코드 카탈로그에서 DB 행을 찾는 용도다.
 */
public record AccessCatalog(Map<PermissionCode, AccessPermission> permissions,
                            Map<RoleTemplateCode, AccessRoleTemplate> templates) {

    public AccessCatalog {
        permissions = Map.copyOf(permissions);
        templates = Map.copyOf(templates);
    }

    public AccessPermission permission(PermissionCode code) {
        return permissions.get(code);
    }

    public AccessRoleTemplate template(RoleTemplateCode code) {
        return templates.get(code);
    }
}
