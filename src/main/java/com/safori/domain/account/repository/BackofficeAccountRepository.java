package com.safori.domain.account.repository;

import com.safori.domain.account.entity.BackofficeAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BackofficeAccountRepository extends JpaRepository<BackofficeAccount, Long> {

    boolean existsByLoginId(String loginId);

    Optional<BackofficeAccount> findByLoginId(String loginId);

    Optional<BackofficeAccount> findByAccountUuid(String accountUuid);
}
