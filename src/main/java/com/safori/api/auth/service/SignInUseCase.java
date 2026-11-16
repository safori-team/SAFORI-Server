package com.safori.api.auth.service;

import com.safori.api.auth.dto.SignInRequest;
import com.safori.common.annotation.UseCase;
import com.safori.security.dto.JwtToken;
import com.safori.security.service.BackofficeLoginService;
import com.safori.security.service.UserTokenService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignInUseCase {

    private final BackofficeLoginService backofficeLoginService;
    private final UserTokenService userTokenService;

    /** 관리자·담당자·보호자·어르신 통합 로그인. 백오피스 계정을 먼저 찾고, 없으면 어르신 계정으로 로그인한다. */
    public JwtToken execute(SignInRequest signInRequest) {
        String username = signInRequest.getUsername();
        String password = signInRequest.getPassword();
        return backofficeLoginService.login(username, password)
                .orElseGet(() -> userTokenService.login(username, password));
    }
}
