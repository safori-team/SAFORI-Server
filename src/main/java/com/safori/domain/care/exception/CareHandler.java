package com.safori.domain.care.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class CareHandler extends GeneralException {

    public static final GeneralException RECIPIENT_ALREADY_REGISTERED =
            new CareHandler(ErrorStatus.CARE_RECIPIENT_ALREADY_REGISTERED);
    public static final GeneralException RECIPIENT_INACTIVE =
            new CareHandler(ErrorStatus.CARE_RECIPIENT_INACTIVE);
    public static final GeneralException WORKER_NOT_ASSIGNABLE =
            new CareHandler(ErrorStatus.CARE_WORKER_NOT_ASSIGNABLE);
    public static final GeneralException GUARDIAN_NOT_LINKABLE =
            new CareHandler(ErrorStatus.CARE_GUARDIAN_NOT_LINKABLE);

    public static final GeneralException RECIPIENT_NOT_FOUND =
            new CareHandler(ErrorStatus.CARE_RECIPIENT_NOT_FOUND);

    public static final GeneralException RECORD_NOT_FOUND =
            new CareHandler(ErrorStatus.CARE_RECORD_NOT_FOUND);
    public static final GeneralException RECORD_NOT_PROCESSABLE =
            new CareHandler(ErrorStatus.CARE_RECORD_NOT_PROCESSABLE);

    public CareHandler(BaseErrorCode code) {
        super(code);
    }
}
