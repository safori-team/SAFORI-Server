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

    /**
     * 아이디가 바뀌면 발급된 리프레시 토큰의 주인도 새 아이디로 옮겨 로그인을 유지한다.
     * 옛 아이디로 남겨 두면 그 아이디를 새로 가입한 사람에게 재발급될 수 있다.
     */
    public void renameOwner(String from, String to) {
        refreshTokenRepository.renameOwner(from, to);
    }

    public void deleteValue(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            // "Bearer " 접두사 제거
            token = token.substring(7);
        }
        refreshTokenRepository.deleteById(token);
    }
}
