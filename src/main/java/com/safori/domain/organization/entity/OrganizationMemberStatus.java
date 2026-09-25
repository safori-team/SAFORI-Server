package com.safori.domain.organization.entity;

/**
 * 기관 소속 상태. ACTIVE일 때만 권한을 행사한다.
 *
 * <pre>
 *   PENDING ──approve──▶ ACTIVE ◀──reactivate── SUSPENDED
 *                          └───────suspend───────▶
 *   (어느 상태든) ──revoke──▶ REVOKED   (되돌리지 않는다)
 * </pre>
 */
public enum OrganizationMemberStatus {

    /** 기관이 초대했고 승인을 기다리는 중. 그룹·역할이 연결돼 있어도 권한은 없다. */
    PENDING,

    ACTIVE,

    /** 일시 정지. 다시 활성화할 수 있다. */
    SUSPENDED,

    /** 소속 종료(퇴사·연결 종료). 작성 기록·이력은 남긴다. */
    REVOKED
}
