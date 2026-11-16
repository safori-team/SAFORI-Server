package com.safori.domain.access.policy;

import com.safori.domain.access.entity.DataScope;

/**
 * 역할 하나를 통해 받은 권한 1건. 같은 권한을 여러 역할에서 받으면 {@link EffectivePermissions}가 범위를 합친다.
 *
 * @param permissionCode {@code access_permission.code}
 * @param dataScope      권한을 준 역할의 데이터 범위
 */
public record PermissionGrant(String permissionCode, DataScope dataScope) {
}
