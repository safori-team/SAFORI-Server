package com.safori.domain.user.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class UserHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new UserHandler(ErrorStatus.USER_NOT_FOUND);
    public static final GeneralException USERNAME_ALREADY_EXISTS =
            new UserHandler(ErrorStatus.USER_USERNAME_ALREADY_EXISTS);
    public static final GeneralException PASSWORD_NOT_MATCH =
            new UserHandler(ErrorStatus.USER_PASSWORD_NOT_MATCH);

    public UserHandler(BaseErrorCode code) {
        super(code);
    }
}
