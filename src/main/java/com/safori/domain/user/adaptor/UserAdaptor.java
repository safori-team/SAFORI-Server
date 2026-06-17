package com.safori.domain.user.adaptor;

import com.safori.domain.user.entity.User;

import java.util.List;

public interface UserAdaptor {

    User queryUserById(Long userId);

    List<User> queryAll();

    User queryUserByUsername(String username);

    User queryUserByUserUuid(String userUuid);

    boolean existsByUsername(String username);
}
