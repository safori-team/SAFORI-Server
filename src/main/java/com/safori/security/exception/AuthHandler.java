package com.safori.security.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class AuthHandler extends GeneralException {

    public static final GeneralException INVALID_REFRESH_TOKEN =
            new AuthHandler(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);

    public AuthHandler(BaseErrorCode code) {
        super(code);
    }
}
