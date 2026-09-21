package com.safori.domain.user.validator;

import com.safori.common.annotation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@Validator
@RequiredArgsConstructor
public class UserValidatorImpl implements UserValidator {

    @Override
    public void validateName(String name) {
        if(name == null) {
            throw new IllegalArgumentException("이름은 null일 수 없습니다");
        }
        if(!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("이름은 빈 문자열일 수 없습니다");
        }
    }

    @Override
    public void validatePassword(String password) {
        if(password == null) {
            throw new IllegalArgumentException("비밀번호는 null일 수 없습니다");
        }
        if(!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("비밀번호는 빈 문자열일 수 없습니다");
        }
    }

    @Override
    public void validateUsername(String username) {
        if(username == null) {
            throw new IllegalArgumentException("멤버코드는 null일 수 없습니다");
        }
        if(!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("멤버코드는 빈 문자열일 수 없습니다");
        }
    }
}
