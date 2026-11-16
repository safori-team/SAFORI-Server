package com.safori.api.worker.dto;

import com.safori.domain.account.entity.BackofficeAccountStatus;

/** 담당자 목록 탭. */
public enum ManagerStatusFilter {
    ALL(null),
    ACTIVE(BackofficeAccountStatus.ACTIVE),
    INACTIVE(BackofficeAccountStatus.SUSPENDED);

    private final BackofficeAccountStatus accountStatus;

    ManagerStatusFilter(BackofficeAccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    /** null이면 상태로 거르지 않는다. */
    public BackofficeAccountStatus accountStatus() {
        return accountStatus;
    }
}
