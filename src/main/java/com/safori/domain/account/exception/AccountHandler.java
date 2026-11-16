package com.safori.domain.account.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class AccountHandler extends GeneralException {

    public static final GeneralException LOGIN_ID_ALREADY_EXISTS =
            new AccountHandler(ErrorStatus.ACCOUNT_LOGIN_ID_ALREADY_EXISTS);
    public static final GeneralException INACTIVE =
            new AccountHandler(ErrorStatus.ACCOUNT_INACTIVE);
    public static final GeneralException PROFILE_NOT_EDITABLE =
            new AccountHandler(ErrorStatus.ACCOUNT_PROFILE_NOT_EDITABLE);

    public AccountHandler(BaseErrorCode code) {
        super(code);
    }
}
