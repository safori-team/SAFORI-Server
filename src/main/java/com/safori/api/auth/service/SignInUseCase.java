package com.safori.api.auth.service;

import com.safori.api.auth.dto.SignInRequest;
import com.safori.common.annotation.UseCase;
import com.safori.security.dto.JwtToken;
import com.safori.security.service.UserTokenService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignInUseCase {

    private final UserTokenService userTokenService;

    public JwtToken execute(SignInRequest signInRequest) {
        return userTokenService.login(signInRequest.getUsername(), signInRequest.getPassword());
    }
}
