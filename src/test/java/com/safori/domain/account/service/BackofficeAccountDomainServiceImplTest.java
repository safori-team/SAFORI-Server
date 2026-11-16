package com.safori.domain.account.service;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackofficeAccountDomainServiceImplTest {

    @Mock BackofficeAccountRepository accountRepository;
    @Mock com.safori.domain.user.repository.UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks BackofficeAccountDomainServiceImpl accountService;

    @Captor ArgumentCaptor<BackofficeAccount> accountCaptor;

    @Test
    @DisplayName("가입하면 비밀번호를 해시해 활성 계정으로 저장한다")
    void registerEncodesPasswordAndSaves() {
        given(accountRepository.existsByLoginId("manager01")).willReturn(false);
        given(passwordEncoder.encode("password1234!")).willReturn("ENCODED");
        given(accountRepository.save(any(BackofficeAccount.class))).willAnswer(invocation -> invocation.getArgument(0));

        accountService.register("manager01", "password1234!", "김관리", null);

        verify(accountRepository).save(accountCaptor.capture());
        BackofficeAccount saved = accountCaptor.getValue();
        assertThat(saved.getLoginId()).isEqualTo("manager01");
        assertThat(saved.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(saved.getStatus()).isEqualTo(BackofficeAccountStatus.ACTIVE);
        assertThat(saved.getAccountUuid()).isNotBlank();
        assertThat(saved.getAuthVersion()).isZero();
    }

    @Test
    @DisplayName("이미 쓰는 로그인 아이디면 가입을 거부한다")
    void duplicateLoginIdIsRejected() {
        given(accountRepository.existsByLoginId("manager01")).willReturn(true);

        assertThatThrownBy(() -> accountService.register("manager01", "password1234!", "김관리", null))
                .isEqualTo(AccountHandler.LOGIN_ID_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("정지는 넘겨받은 인스턴스가 아니라 다시 읽은 계정을 바꿔 돌려준다 — 상태 변경과 토큰 무효화(auth_version 증가)")
    void suspendChangesReloadedAccount() {
        BackofficeAccount stored = account(1L);
        BackofficeAccount readInAnotherTransaction = account(1L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(stored));

        BackofficeAccount suspended = accountService.suspend(readInAnotherTransaction);

        assertThat(suspended).isSameAs(stored);
        assertThat(stored.getStatus()).isEqualTo(BackofficeAccountStatus.SUSPENDED);
        assertThat(stored.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("강제 로그아웃은 계정 상태는 두고 auth_version만 올린다")
    void revokeIssuedTokensKeepsStatus() {
        BackofficeAccount stored = account(1L);
        given(accountRepository.findById(1L)).willReturn(Optional.of(stored));

        BackofficeAccount revoked = accountService.revokeIssuedTokens(account(1L));

        assertThat(revoked.isActive()).isTrue();
        assertThat(revoked.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("정지된 계정을 다시 활성화한다")
    void activateRestoresAccount() {
        BackofficeAccount stored = account(1L);
        stored.suspend();
        given(accountRepository.findById(1L)).willReturn(Optional.of(stored));

        assertThat(accountService.activate(account(1L)).isActive()).isTrue();
    }

    private static BackofficeAccount account(Long id) {
        return BackofficeAccount.builder()
                .id(id)
                .accountUuid("account-" + id)
                .loginId("manager01")
                .passwordHash("ENCODED")
                .name("김관리")
                .status(BackofficeAccountStatus.ACTIVE)
                .authVersion(0L)
                .build();
    }
}
