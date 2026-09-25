package com.safori.api.user.service;

import com.safori.api.user.dto.UserInfoResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.security.dto.AccountRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static com.safori.security.filter.BackofficeAuthenticationFilter.ROLE_PREFIX;

/**
 * 어르신 앱 토큰과 백오피스 토큰 모두 받는다. 백오피스는 인증 필터가 요청마다 DB에서 확인한 주체·권한과
 * 토큰의 역할({@code ROLE_} authority)을 그대로 돌려주므로 추가 조회가 없다.
 */
@UseCase
@RequiredArgsConstructor
public class GetUserInfoUseCase {

    private final UserAdaptor userAdaptor;

    public UserInfoResponse execute(Authentication authentication) {
        return BackofficeActor.from(authentication)
                .map(actor -> backoffice(actor, authentication))
                .orElseGet(() -> elder(authentication.getName()));
    }

    private UserInfoResponse elder(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        return UserInfoResponse.builder()
                .role(AccountRole.ELDER)
                .permissions(List.of())
                .name(user.getName())
                .username(user.getUsername())
                .gender(user.getGender())
                .nickname(user.getNickname())
                .birthDate(user.getBirthDate())
                .build();
    }

    private static UserInfoResponse backoffice(BackofficeActor actor, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return UserInfoResponse.builder()
                .role(authorities.stream()
                        .filter(authority -> authority.startsWith(ROLE_PREFIX))
                        .map(authority -> AccountRole.valueOf(authority.substring(ROLE_PREFIX.length())))
                        .findFirst()
                        .orElse(null))
                .permissions(authorities.stream()
                        .filter(authority -> !authority.startsWith(ROLE_PREFIX))
                        .sorted()
                        .toList())
                .username(actor.loginId())
                .name(actor.name())
                .organization(new UserInfoResponse.Organization(actor.organizationPublicId(), actor.organizationName()))
                .build();
    }
}
