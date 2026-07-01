package com.safori.api.user.service;

import com.safori.api.user.dto.UserInfoResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetUserInfoUseCase {

    private final UserAdaptor userAdaptor;

    public UserInfoResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        return UserInfoResponse.builder()
                .name(user.getName())
                .username(user.getUsername())
                .gender(user.getGender())
                .nickname(user.getNickname())
                .build();
    }
}
