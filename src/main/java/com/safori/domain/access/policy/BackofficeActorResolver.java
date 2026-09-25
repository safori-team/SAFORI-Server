package com.safori.domain.access.policy;

import com.safori.domain.organization.adaptor.OrganizationMemberAdaptor;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 토큰이 가리키는 주체를 요청 시점의 DB 상태로 확인한다.
 *
 * <p>토큰 클레임만 믿으면 정지·소속 종료·권한 회수가 액세스 토큰 만료 때까지 반영되지 않는다.
 * 그래서 매 요청 계정·기관·멤버십 상태와 {@code auth_version}을 다시 보고, 권한도 새로 계산한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackofficeActorResolver {

    private final OrganizationMemberAdaptor organizationMemberAdaptor;
    private final EffectivePermissionResolver permissionResolver;

    /**
     * @return 계정·기관·멤버십이 모두 활성이고 토큰 발급 이후 {@code auth_version}이 그대로일 때만 값이 있다.
     */
    public Optional<AuthenticatedActor> resolveActive(String accountUuid, String organizationPublicId,
                                                      long authVersion) {
        return organizationMemberAdaptor.findForAuthentication(accountUuid, organizationPublicId)
                .filter(member -> member.getAccount().getAuthVersion() == authVersion)
                .filter(OrganizationMember::isActiveActor)
                .map(member -> new AuthenticatedActor(
                        BackofficeActor.from(member), permissionResolver.resolve(member)));
    }
}
