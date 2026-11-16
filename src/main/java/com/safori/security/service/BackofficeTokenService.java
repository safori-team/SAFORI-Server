package com.safori.security.service;

import com.safori.security.dto.AccountRole;
import com.safori.security.dto.BackofficeTokenClaims;

import java.util.Optional;

/**
 * 백오피스(기관 관리자·담당자·보호자) 액세스 토큰.
 *
 * <p>어르신 앱 토큰과 서명 키·issuer·audience·토큰 종류가 모두 달라 서로의 체인에서 통하지 않는다.
 * 로그인·재발급 API는 인증 담당 범위이며, 계정·멤버십 확인 후 {@link #issueAccessToken}을 호출하면 된다.
 */
public interface BackofficeTokenService {

    /**
     * @throws IllegalStateException {@code token.secret-backoffice}가 설정되지 않아 비활성 상태일 때
     */
    String issueAccessToken(String accountUuid, String organizationPublicId, long authVersion, AccountRole role);

    /** 서명·만료·issuer·audience·토큰 종류를 검증한다. 하나라도 어긋나거나 비활성 상태면 빈 값. */
    Optional<BackofficeTokenClaims> parse(String token);

    /** 시크릿이 설정되지 않으면 false. 이때 백오피스 경로 요청은 모두 401이다. */
    boolean isEnabled();
}
