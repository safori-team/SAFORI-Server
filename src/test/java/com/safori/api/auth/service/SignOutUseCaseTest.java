package com.safori.api.auth.service;

import com.safori.security.service.UserTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SignOutUseCaseTest {

    @Mock UserTokenService userTokenService;
    @InjectMocks SignOutUseCase signOutUseCase;

    @Test
    @DisplayName("로그아웃 성공 - 성공 메시지 반환")
    void execute_success_returnsMessage() {
        given(userTokenService.logout("refresh-1")).willReturn(true);

        assertThat(signOutUseCase.execute("refresh-1")).isEqualTo("로그아웃에 성공하였습니다");
    }

    @Test
    @DisplayName("로그아웃 실패 - 실패 메시지 반환")
    void execute_failure_returnsMessage() {
        given(userTokenService.logout("bad")).willReturn(false);

        assertThat(signOutUseCase.execute("bad")).isEqualTo("잘못된 리프레시 토큰입니다");
    }
}
