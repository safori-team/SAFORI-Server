package com.safori.api.operator.dto;

import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;

import java.util.List;

public record OrganizationRolePermissionsResponse(
        String roleCode,
        DataScope dataScope,
        List<PermissionCode> permissions
) {
}
