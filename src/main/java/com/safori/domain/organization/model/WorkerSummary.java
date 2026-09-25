package com.safori.domain.organization.model;

import com.safori.domain.account.entity.BackofficeAccountStatus;

/**
 * 담당자 목록 한 줄. 현재 배정 인원까지 한 쿼리로 읽는다.
 *
 * @param assignedCount 현재(종료되지 않은) 배정 대상자 수
 */
public record WorkerSummary(String accountUuid,
                            String name,
                            String jobTitle,
                            BackofficeAccountStatus accountStatus,
                            long assignedCount) {
}
