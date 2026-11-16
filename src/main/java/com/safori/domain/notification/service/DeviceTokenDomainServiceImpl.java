package com.safori.domain.notification.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.repository.DeviceTokenRepository;
import com.safori.domain.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DomainService
@RequiredArgsConstructor
public class DeviceTokenDomainServiceImpl implements DeviceTokenDomainService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final BackofficeAccountRepository accountRepository;

    @Override
    public DeviceToken registerToken(User user, String token) {
        return deviceTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reassignTo(user);
                    return existing;
                })
                .orElseGet(() -> deviceTokenRepository.save(
                        DeviceToken.builder()
                                .user(user)
                                .token(token)
                                .build()));
    }

    @Override
    public DeviceToken registerAccountToken(Long accountId, String token) {
        BackofficeAccount account = accountRepository.getReferenceById(accountId);
        return deviceTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reassignTo(account);
                    return existing;
                })
                .orElseGet(() -> deviceTokenRepository.save(
                        DeviceToken.builder()
                                .account(account)
                                .token(token)
                                .build()));
    }

    @Override
    public void deleteToken(User user, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUser() != null
                        && deviceToken.getUser().getId().equals(user.getId()))
                .ifPresent(deviceTokenRepository::delete);
    }

    @Override
    public void deleteAccountToken(Long accountId, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getAccount() != null
                        && deviceToken.getAccount().getId().equals(accountId))
                .ifPresent(deviceTokenRepository::delete);
    }

    @Override
    public void deleteByTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        deviceTokenRepository.deleteAllByTokenIn(tokens);
    }
}
