package com.safori.domain.organization.model;

import com.safori.domain.account.entity.BackofficeAccountStatus;

/** 계정 상태별 인원 수(목록 탭 개수용). */
public record StatusCount(BackofficeAccountStatus status, long count) {
}
