package com.safori.security.service;

import com.safori.common.service.RedisService;
import com.safori.security.dto.JwtToken;
import com.safori.security.exception.AuthHandler;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.safori.domain.user.exception.UserHandler.PASSWORD_NOT_MATCH;

@Slf4j
@Service
public class UserTokenServiceImpl implements UserTokenService {

    private static final long ACCESS_TOKEN_VALIDITY_MS = 1800000L;    // 30분
    private static final long REFRESH_TOKEN_VALIDITY_MS = 604800000L; // 7일

    private final Key key;
    private final PasswordEncoder passwordEncoder;
    private final UserAdaptor userAdaptor;
    private final RedisService redisService;

    public UserTokenServiceImpl(Environment environment,
                               PasswordEncoder passwordEncoder,
                               UserAdaptor userAdaptor,
                               RedisService redisService) {
        byte[] keyBytes = Decoders.BASE64.decode(environment.getProperty("token.secret-user"));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.passwordEncoder = passwordEncoder;
        this.userAdaptor = userAdaptor;
        this.redisService = redisService;
    }

    @Override
    public JwtToken login(String username, String password) {
        User user = userAdaptor.queryUserByUsername(username);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw PASSWORD_NOT_MATCH;
        }
        return generateToken(
                new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities())
        );
    }

    @Override
    public JwtToken reissueToken(String refreshToken) {
        // 1. Refresh Token 유효성 검사 (Redis 화이트리스트 존재 여부)
        if (!existsRefreshToken(refreshToken)) {
            throw AuthHandler.INVALID_REFRESH_TOKEN;
        }

        // 2. 회전: 이전 리프레시 토큰 삭제
        redisService.deleteValue(refreshToken);

        // 3. 새 Authentication 생성 후 재발급
        Claims claims = parseClaims(refreshToken);
        String username = claims.getSubject();
        User user = userAdaptor.queryUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, "",
                user.getAuthorities());

        return generateToken(authentication);
    }

    @Override
    public JwtToken generateToken(Authentication authentication) {
        // 권한 가져오기
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_VALIDITY_MS);
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setIssuedAt(new Date(now))
                .setExpiration(accessTokenExpiresIn)
                .setId(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Refresh Token 생성
        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + REFRESH_TOKEN_VALIDITY_MS))
                .setId(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // 새 리프레시 토큰을 Redis 화이트리스트에 저장
        redisService.setValue(refreshToken, authentication.getName());

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public Authentication getAuthentication(String accessToken) {
        // Jwt 토큰 복호화
        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new IllegalArgumentException("auth is null");
        }

        // 클레임에서 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("auth").toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // UserDetails 객체를 만들어서 Authentication return
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    @Override
    public boolean logout(String refreshToken) {
        redisService.deleteValue(refreshToken);
        return true;
    }

    @Override
    public boolean existsRefreshToken(String refreshToken) {
        return redisService.getValue(refreshToken) != null;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
