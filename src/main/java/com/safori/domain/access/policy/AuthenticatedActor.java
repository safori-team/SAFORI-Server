package com.safori.domain.access.policy;

/**
 * 인증 필터가 요청마다 DB에서 확인한 주체와 그 시점의 최종 권한.
 */
public record AuthenticatedActor(BackofficeActor actor, EffectivePermissions permissions) {
}
