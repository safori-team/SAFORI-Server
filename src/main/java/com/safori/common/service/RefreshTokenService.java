package com.safori.common.service;

import com.safori.security.entity.RefreshToken;
import com.safori.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final Duration TTL = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;

    // FOR Refresh token (whiteList)
    // key-value 설정 (7일 만료)
    public void setValue(String token, String username) {
        refreshTokenRepository.save(new RefreshToken(token, username, LocalDateTime.now().plus(TTL)));
    }

    // key 값으로 value 가져오기 (만료됐으면 null)
    public String getValue(String token) {
        return refreshTokenRepository.findById(token)
                .filter(refreshToken -> refreshToken.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(RefreshToken::getUsername)
                .orElse(null);
    }

    public void deleteValue(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            // "Bearer " 접두사 제거
            token = token.substring(7);
        }
        refreshTokenRepository.deleteById(token);
    }
}
