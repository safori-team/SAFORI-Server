package com.safori.domain.access.policy;

import com.safori.domain.organization.entity.OrganizationMember;
import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * 인증된 백오피스 요청의 주체(Spring Security principal). 토큰 값이 아니라 요청 시점 DB 상태로 만든다.
 *
 * <p>요청 파라미터로 들어온 기관·구성원 ID 대신 항상 이 값을 기준으로 판정한다.
 */
public record BackofficeActor(Long accountId,
                              String accountUuid,
                              Long organizationMemberId,
                              Long organizationId,
                              String organizationPublicId) {

    public static BackofficeActor from(OrganizationMember member) {
        return new BackofficeActor(
                member.getAccount().getId(),
                member.getAccount().getAccountUuid(),
                member.getId(),
                member.getOrganization().getId(),
                member.getOrganization().getPublicId());
    }

    /** 백오피스 인증이 아니면(익명·어르신 앱 사용자 등) 빈 값. */
    public static Optional<BackofficeActor> from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof BackofficeActor actor ? Optional.of(actor) : Optional.empty();
    }
}
