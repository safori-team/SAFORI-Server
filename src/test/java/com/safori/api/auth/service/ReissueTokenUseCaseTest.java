package com.safori.api.auth.service;

import com.safori.api.auth.dto.TokenReissueRequest;
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
class ReissueTokenUseCaseTest {

    @Mock UserTokenService userTokenService;
    @InjectMocks ReissueTokenUseCase reissueTokenUseCase;

    @Test
    @DisplayName("재발급 - refreshToken을 토큰 서비스에 위임하고 새 JwtToken 반환")
    void execute_delegatesToTokenService() {
        TokenReissueRequest request = TokenReissueRequest.builder().refreshToken("refresh-1").build();
        JwtToken expected = JwtToken.builder()
                .grantType("Bearer").accessToken("NEW_ACCESS").refreshToken("NEW_REFRESH").build();
        given(userTokenService.reissueToken("refresh-1")).willReturn(expected);

        JwtToken result = reissueTokenUseCase.execute(request);

        assertThat(result).isSameAs(expected);
        verify(userTokenService).reissueToken("refresh-1");
    }
}
