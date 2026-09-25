package com.safori.domain.organization.entity;

public enum OrganizationStatus {
    ACTIVE,

    /** 기관 전체의 권한 행사 중지. 관리자를 포함한 모든 소속 구성원의 권한 판정이 거부된다. */
    INACTIVE
}
