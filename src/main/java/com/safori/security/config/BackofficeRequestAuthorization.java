package com.safori.security.config;

import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.policy.BackofficeAccessPolicy;
import com.safori.domain.access.policy.BackofficeActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * {@link BackofficeAuthorizationRules}에서 쓰는 URL 인가 판정 모음.
 */
@Component
@RequiredArgsConstructor
public class BackofficeRequestAuthorization {

    private final BackofficeAccessPolicy accessPolicy;

    /** 인증된 백오피스 구성원이면 허용한다. 권한이 필요 없는 경로(본인 정보 조회 등)용. */
    public AuthorizationManager<RequestAuthorizationContext> member() {
        return (authentication, context) ->
                new AuthorizationDecision(BackofficeActor.from(authentication.get()).isPresent());
    }

    /** 나열한 권한 중 하나라도 있으면 허용한다. 데이터 범위는 보지 않는다(기관 단위 기능·목록 진입용). */
    public AuthorizationManager<RequestAuthorizationContext> permission(PermissionCode... anyOf) {
        String[] authorities = Arrays.stream(anyOf).map(PermissionCode::name).toArray(String[]::new);
        return AuthorityAuthorizationManager.hasAnyAuthority(authorities);
    }

    /**
     * 경로 변수로 지정된 어르신({@code care_recipient.public_id})에 대해 권한과 데이터 범위를 모두 만족해야 허용한다.
     * 담당자는 현재 배정, 보호자는 현재 연결된 어르신만, 관리자는 소속 기관 어르신만 통과한다.
     */
    public AuthorizationManager<RequestAuthorizationContext> recipient(PermissionCode permission,
                                                                       String recipientPublicIdVariable) {
        return (authentication, context) -> {
            String recipientPublicId = context.getVariables().get(recipientPublicIdVariable);
            boolean granted = BackofficeActor.from(authentication.get())
                    .map(actor -> accessPolicy.canAccessRecipientByPublicId(
                            actor.organizationMemberId(), permission, recipientPublicId))
                    .orElse(false);
            return new AuthorizationDecision(granted);
        };
    }
}
