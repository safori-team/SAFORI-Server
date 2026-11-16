package com.safori.security.service;

import com.safori.common.service.RefreshTokenService;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.security.dto.AccountRole;
import com.safori.security.dto.JwtToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static com.safori.domain.user.exception.UserHandler.PASSWORD_NOT_MATCH;

/**
 * 백오피스 계정(관리자·담당자·보호자)의 로그인·재발급. 통합 로그인에서 먼저 시도하고, 백오피스 계정이 아니면 empty를
 * 돌려 어르신 로그인으로 넘긴다. 아이디는 두 계정 체계를 통틀어 유일하다.
 *
 * <p>refresh token은 JWT가 아닌 임의 문자열이며, 어르신 앱과 같은 {@code refresh_token} 화이트리스트에
 * 로그인 아이디로 저장한다. 재발급할 때도 계정·멤버십 상태를 다시 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BackofficeAccountRepository accountRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessGroupDomainService accessGroupDomainService;
    private final BackofficeTokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    /** 백오피스 계정이 아니면 empty. */
    public Optional<JwtToken> login(String loginId, String password) {
        return accountRepository.findByLoginId(loginId).map(account -> {
            if (!passwordEncoder.matches(password, account.getPasswordHash())) {
                throw PASSWORD_NOT_MATCH;
            }
            return issue(account);
        });
    }

    /** 백오피스 계정의 refresh token이 아니면(만료·미등록 포함) empty. 쓴 refresh token은 폐기한다(회전). */
    public Optional<JwtToken> reissue(String refreshToken) {
        return Optional.ofNullable(refreshTokenService.getValue(refreshToken))
                .flatMap(accountRepository::findByLoginId)
                .map(account -> {
                    refreshTokenService.deleteValue(refreshToken);
                    return issue(account);
                });
    }

    /** 계정이 활성이고, 소속 기관 멤버십이 승인된(ACTIVE) 상태일 때만 발급한다. */
    private JwtToken issue(BackofficeAccount account) {
        OrganizationMember member = memberRepository.findCurrentByAccount(account)
                .filter(OrganizationMember::isActiveActor)
                .orElseThrow(() -> AccountHandler.INACTIVE);
        AccountRole role = accessGroupDomainService.primaryTemplateOf(member)
                .map(AccountRole::of)
                .orElseThrow(() -> AccountHandler.INACTIVE);

        String accessToken = tokenService.issueAccessToken(
                account.getAccountUuid(), member.getOrganization().getPublicId(), account.getAuthVersion(), role);
        String refreshToken = newRefreshToken();
        refreshTokenService.setValue(refreshToken, account.getLoginId());
        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(role)
                .build();
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
