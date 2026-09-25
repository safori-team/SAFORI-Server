package com.safori.domain.access.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class AccessHandler extends GeneralException {

    public static final GeneralException ROLE_CODE_ALREADY_EXISTS =
            new AccessHandler(ErrorStatus.ACCESS_ROLE_CODE_ALREADY_EXISTS);
    public static final GeneralException PERMISSION_NOT_ASSIGNABLE =
            new AccessHandler(ErrorStatus.ACCESS_PERMISSION_NOT_ASSIGNABLE);
    public static final GeneralException INVALID_ROLE_EXPIRY =
            new AccessHandler(ErrorStatus.ACCESS_INVALID_ROLE_EXPIRY);

    public AccessHandler(BaseErrorCode code) {
        super(code);
    }
}
