package com.safori.domain.organization.exception;

import com.safori.common.exception.BaseErrorCode;
import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;

public class OrganizationHandler extends GeneralException {

    public static final GeneralException INACTIVE =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_INACTIVE);
    public static final GeneralException MISMATCH =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_MISMATCH);
    public static final GeneralException MEMBER_ALREADY_EXISTS =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_MEMBER_ALREADY_EXISTS);
    public static final GeneralException MEMBER_INVALID_STATUS =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_MEMBER_INVALID_STATUS);
    public static final GeneralException MEMBER_SELF_APPROVAL =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_MEMBER_SELF_APPROVAL);

    public static final GeneralException ADMIN_ALREADY_EXISTS =
            new OrganizationHandler(ErrorStatus.ORGANIZATION_ADMIN_ALREADY_EXISTS);

    public OrganizationHandler(BaseErrorCode code) {
        super(code);
    }
}
