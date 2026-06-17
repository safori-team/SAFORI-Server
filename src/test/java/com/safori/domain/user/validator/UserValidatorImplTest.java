package com.safori.domain.user.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValidatorImplTest {

    private final UserValidatorImpl userValidator = new UserValidatorImpl();

    @Test
    @DisplayName("username이 null이면 IllegalArgumentException")
    void validateUsername_nullThrows() {
        assertThatThrownBy(() -> userValidator.validateUsername(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("username이 공백이면 IllegalArgumentException")
    void validateUsername_blankThrows() {
        assertThatThrownBy(() -> userValidator.validateUsername("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("password가 null이면 IllegalArgumentException")
    void validatePassword_nullThrows() {
        assertThatThrownBy(() -> userValidator.validatePassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("name이 공백이면 IllegalArgumentException")
    void validateName_blankThrows() {
        assertThatThrownBy(() -> userValidator.validateName(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정상 값이면 예외 없이 통과")
    void validate_validValuesPass() {
        assertThatCode(() -> {
            userValidator.validateUsername("user01");
            userValidator.validatePassword("myPass1234");
            userValidator.validateName("홍길동");
        }).doesNotThrowAnyException();
    }
}
