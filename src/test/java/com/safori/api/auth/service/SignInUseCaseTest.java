package com.safori.api.auth.service;

import com.safori.api.auth.dto.SignInRequest;
import com.safori.security.dto.JwtToken;
import com.safori.security.service.UserTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignInUseCaseTest {

    @Mock UserTokenService userTokenService;
    @InjectMocks SignInUseCase signInUseCase;

    @Test
    @DisplayName("로그인 - 토큰 서비스에 username·password 위임하고 JwtToken 반환")
    void execute_delegatesToTokenServiceAndReturnsToken() {
        SignInRequest request = SignInRequest.builder()
                .username("user01")
                .password("myPass1234")
                .build();
        JwtToken expected = JwtToken.builder().grantType("Bearer").accessToken("ACCESS").build();
        given(userTokenService.login("user01", "myPass1234")).willReturn(expected);

        JwtToken result = signInUseCase.execute(request);

        assertThat(result).isSameAs(expected);
        verify(userTokenService).login("user01", "myPass1234");
    }
}
