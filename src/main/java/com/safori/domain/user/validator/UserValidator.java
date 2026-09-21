package com.safori.domain.user.validator;

public interface UserValidator {
    void validateName(String name);
    void validatePassword(String password);
    void validateUsername(String username);
}
