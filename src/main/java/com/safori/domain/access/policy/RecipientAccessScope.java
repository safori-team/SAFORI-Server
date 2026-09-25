package com.safori.domain.access.policy;

import com.safori.domain.access.entity.DataScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * 목록·검색·count·export 조회에 거는 어르신 범위 조건.
 *
 * <p>전체를 읽은 뒤 걸러내지 않고 이 조건을 쿼리에 그대로 넣는다
 * ({@code CareRecipientAdaptor#queryAccessible}). 범위가 비어 있으면 아무것도 조회하지 않는다.
 */
public record RecipientAccessScope(Long organizationId, Long organizationMemberId, Set<DataScope> scopes) {

    public RecipientAccessScope {
        scopes = scopes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(scopes));
    }

    public static RecipientAccessScope none() {
        return new RecipientAccessScope(null, null, Set.of());
    }

    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    public boolean organizationWide() {
        return scopes.contains(DataScope.ORGANIZATION);
    }

    public boolean includesAssigned() {
        return scopes.contains(DataScope.ASSIGNED_RECIPIENT);
    }

    public boolean includesLinked() {
        return scopes.contains(DataScope.LINKED_RECIPIENT);
    }
}
