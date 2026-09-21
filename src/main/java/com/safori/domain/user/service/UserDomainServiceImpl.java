package com.safori.domain.user.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.safori.domain.user.exception.UserHandler.USERNAME_ALREADY_EXISTS;

@Transactional
@DomainService
@RequiredArgsConstructor
public class UserDomainServiceImpl implements UserDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(String username, String password, String name, Gender gender, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw USERNAME_ALREADY_EXISTS;
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .name(name)
                .gender(gender)
                .nickname(nickname)
                .role(Role.USER)
                .userUuid(UUID.randomUUID().toString())
                .build();
        return userRepository.save(user);
    }
}
