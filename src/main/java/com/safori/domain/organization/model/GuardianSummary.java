package com.safori.domain.organization.model;

import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.care.entity.GuardianRelation;

/**
 * 보호자 목록 한 줄. 연결된 대상자가 없으면 대상자·관계 필드가 null이다.
 */
public record GuardianSummary(String accountUuid,
                              String name,
                              BackofficeAccountStatus accountStatus,
                              String recipientPublicId,
                              String recipientName,
                              GuardianRelation relation,
                              String relationText) {
}
