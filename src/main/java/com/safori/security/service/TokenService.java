package com.safori.security.service;

import com.safori.security.dto.JwtToken;
import org.springframework.security.core.Authentication;

public interface TokenService {
    JwtToken login(String username, String password);

    JwtToken generateToken(Authentication authentication);

    Authentication getAuthentication(String accessToken);
}
