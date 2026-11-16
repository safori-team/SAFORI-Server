package com.safori.domain.access.policy;

import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 구성원의 최종 권한 = 그룹에서 상속한 역할의 권한 ∪ 개인에게 직접 부여한 역할의 권한.
 *
 * <p>권한마다 그 권한을 준 역할들의 데이터 범위를 모두 모은다. 담당자이면서 보호자인 구성원은
 * {@code RECIPIENT_READ}에 대해 {ASSIGNED_RECIPIENT, LINKED_RECIPIENT}를 함께 갖는 식이다.
 */
public record EffectivePermissions(Map<PermissionCode, Set<DataScope>> grants) {

    private static final EffectivePermissions NONE = new EffectivePermissions(Map.of());

    public EffectivePermissions {
        Map<PermissionCode, Set<DataScope>> copy = new EnumMap<>(PermissionCode.class);
        grants.forEach((permission, scopes) -> {
            if (!scopes.isEmpty()) {
                copy.put(permission, Collections.unmodifiableSet(EnumSet.copyOf(scopes)));
            }
        });
        grants = Collections.unmodifiableMap(copy);
    }

    public static EffectivePermissions none() {
        return NONE;
    }

    /** 코드 카탈로그({@link PermissionCode})에 없는 권한 코드는 무시한다. */
    public static EffectivePermissions of(Collection<PermissionGrant> grants) {
        Map<PermissionCode, Set<DataScope>> merged = new EnumMap<>(PermissionCode.class);
        for (PermissionGrant grant : grants) {
            PermissionCode.find(grant.permissionCode()).ifPresent(permission ->
                    merged.computeIfAbsent(permission, ignored -> EnumSet.noneOf(DataScope.class))
                            .add(grant.dataScope()));
        }
        return new EffectivePermissions(merged);
    }

    public boolean has(PermissionCode permission) {
        return grants.containsKey(permission);
    }

    public Set<DataScope> scopesOf(PermissionCode permission) {
        return grants.getOrDefault(permission, Set.of());
    }

    /** 어떤 권한이든 이 범위로 받은 것이 있는지. 배정·연결 대상이 될 수 있는 구성원인지 판단할 때 쓴다. */
    public boolean hasScope(DataScope scope) {
        return grants.values().stream().anyMatch(scopes -> scopes.contains(scope));
    }

    public Set<PermissionCode> permissions() {
        return grants.keySet();
    }

    public boolean isEmpty() {
        return grants.isEmpty();
    }
}
