package com.safori.api.user.service;

import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.common.annotation.UseCase;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.service.UserDomainService;
import com.safori.domain.user.validator.UserValidator;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class SignUpUseCase {

    private final UserDomainService userDomainService;
    private final UserValidator userValidator;

    public Long execute(UserRegisterRequest userRegisterRequest) {
        userValidator.validateUsername(userRegisterRequest.getUsername());
        userValidator.validatePassword(userRegisterRequest.getPassword());
        userValidator.validateName(userRegisterRequest.getName());
        User user = userDomainService.registerUser(
                userRegisterRequest.getUsername(),
                userRegisterRequest.getPassword(),
                userRegisterRequest.getName());
        return user.getId();
    }
}
