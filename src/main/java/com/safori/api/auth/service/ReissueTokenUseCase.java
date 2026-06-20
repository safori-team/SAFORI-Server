package com.safori.api.auth.service;

import com.safori.api.auth.dto.TokenReissueRequest;
import com.safori.common.annotation.UseCase;
import com.safori.security.dto.JwtToken;
import com.safori.security.service.UserTokenService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ReissueTokenUseCase {

    private final UserTokenService userTokenService;

    public JwtToken execute(TokenReissueRequest tokenReissueRequest) {
        return userTokenService.reissueToken(tokenReissueRequest.getRefreshToken());
    }
}
