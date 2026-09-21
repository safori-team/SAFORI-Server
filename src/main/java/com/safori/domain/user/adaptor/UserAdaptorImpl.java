package com.safori.domain.user.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.safori.domain.user.exception.UserHandler.NOT_FOUND;

@Adaptor
@RequiredArgsConstructor
public class UserAdaptorImpl implements UserAdaptor {

    private final UserRepository userRepository;

    @Override
    public User queryUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public List<User> queryAll() {
        return userRepository.findAll();
    }

    @Override
    public User queryUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public User queryUserByUserUuid(String userUuid) {
        return userRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
}
