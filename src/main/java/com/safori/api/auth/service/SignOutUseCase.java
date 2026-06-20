package com.safori.api.auth.service;

import com.safori.common.annotation.UseCase;
import com.safori.security.service.UserTokenService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignOutUseCase {

    private final UserTokenService userTokenService;

    public String execute(String refreshToken) {
        return userTokenService.logout(refreshToken) ? "로그아웃에 성공하였습니다" : "잘못된 리프레시 토큰입니다";
    }
}
