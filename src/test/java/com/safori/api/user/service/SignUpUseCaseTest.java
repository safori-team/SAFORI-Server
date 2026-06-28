package com.safori.api.user.service;

import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.service.UserDomainService;
import com.safori.domain.user.validator.UserValidator;
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
class SignUpUseCaseTest {

    @Mock UserDomainService userDomainService;
    @Mock UserValidator userValidator;
    @Mock User user;
    @InjectMocks SignUpUseCase signUpUseCase;

    @Test
    @DisplayName("회원가입 - 검증 수행 후 도메인서비스에 위임하고 userId 반환")
    void execute_validatesDelegatesAndReturnsUserId() {
        UserRegisterRequest request = UserRegisterRequest.builder()
                .username("user01")
                .password("myPass1234")
                .name("홍길동")
                .gender(Gender.MALE)
                .nickname("길동이")
                .build();
        given(userDomainService.registerUser("user01", "myPass1234", "홍길동", Gender.MALE, "길동이")).willReturn(user);
        given(user.getId()).willReturn(7L);

        Long userId = signUpUseCase.execute(request);

        assertThat(userId).isEqualTo(7L);
        verify(userValidator).validateUsername("user01");
        verify(userValidator).validatePassword("myPass1234");
        verify(userValidator).validateName("홍길동");
        verify(userDomainService).registerUser("user01", "myPass1234", "홍길동", Gender.MALE, "길동이");
    }

    @Test
    @DisplayName("회원가입 - 별명 없이도 성별과 함께 도메인서비스에 위임")
    void execute_nullNickname_delegates() {
        UserRegisterRequest request = UserRegisterRequest.builder()
                .username("user02")
                .password("myPass1234")
                .name("김영희")
                .gender(Gender.FEMALE)
                .build();
        given(userDomainService.registerUser("user02", "myPass1234", "김영희", Gender.FEMALE, null)).willReturn(user);
        given(user.getId()).willReturn(8L);

        Long userId = signUpUseCase.execute(request);

        assertThat(userId).isEqualTo(8L);
        verify(userDomainService).registerUser("user02", "myPass1234", "김영희", Gender.FEMALE, null);
    }
}
