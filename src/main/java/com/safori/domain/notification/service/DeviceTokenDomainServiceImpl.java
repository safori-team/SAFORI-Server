package com.safori.domain.notification.service;

import com.safori.common.annotation.DomainService;
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
    public void deleteToken(User user, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUser().getId().equals(user.getId()))
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
