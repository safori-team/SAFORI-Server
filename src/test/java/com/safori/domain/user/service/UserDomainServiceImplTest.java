package com.safori.domain.user.service;

import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.exception.UserHandler;
import com.safori.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDomainServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserDomainServiceImpl userDomainService;

    @Captor ArgumentCaptor<User> userCaptor;

    @Test
    @DisplayName("회원가입 성공 - 비밀번호 해시·UUID·Role.USER 부여 후 저장")
    void registerUser_encodesPasswordAndSaves() {
        String username = "user01";
        String rawPassword = "myPass1234";
        String name = "홍길동";
        given(userRepository.existsByUsername(username)).willReturn(false);
        given(passwordEncoder.encode(rawPassword)).willReturn("ENCODED");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        userDomainService.registerUser(username, rawPassword, name);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getUserUuid()).isNotBlank();
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 존재하는 username이면 USERNAME_ALREADY_EXISTS 예외")
    void registerUser_throwsWhenUsernameDuplicated() {
        String username = "user01";
        given(userRepository.existsByUsername(username)).willReturn(true);

        assertThatThrownBy(() -> userDomainService.registerUser(username, "myPass1234", "홍길동"))
                .isEqualTo(UserHandler.USERNAME_ALREADY_EXISTS);

        verify(userRepository, never()).save(any(User.class));
    }
}
