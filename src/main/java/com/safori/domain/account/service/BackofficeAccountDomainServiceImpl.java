package com.safori.domain.account.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static com.safori.domain.account.exception.AccountHandler.LOGIN_ID_ALREADY_EXISTS;

@Transactional
@DomainService
@RequiredArgsConstructor
public class BackofficeAccountDomainServiceImpl implements BackofficeAccountDomainService {

    private final BackofficeAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public BackofficeAccount register(String loginId, String rawPassword, String name) {
        if (accountRepository.existsByLoginId(loginId)) {
            throw LOGIN_ID_ALREADY_EXISTS;
        }
        return accountRepository.save(
                BackofficeAccount.create(loginId, passwordEncoder.encode(rawPassword), name));
    }

    @Override
    public BackofficeAccount suspend(BackofficeAccount account) {
        BackofficeAccount current = reload(account);
        current.suspend();
        return current;
    }

    @Override
    public BackofficeAccount activate(BackofficeAccount account) {
        BackofficeAccount current = reload(account);
        current.activate();
        return current;
    }

    @Override
    public BackofficeAccount revokeIssuedTokens(BackofficeAccount account) {
        BackofficeAccount current = reload(account);
        current.revokeIssuedTokens();
        return current;
    }

    /** 다른 트랜잭션에서 읽은 인스턴스를 바꾸면 저장되지 않으므로, 이 트랜잭션에서 다시 읽은 계정을 바꾼다. */
    private BackofficeAccount reload(BackofficeAccount account) {
        return accountRepository.findById(account.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 백오피스 계정입니다: " + account.getId()));
    }
}
