package com.safori.domain.access.entity;

/**
 * 역할·그룹·역할 템플릿의 사용 상태. INACTIVE면 권한 계산에서 빠진다.
 */
public enum AccessStatus {
    ACTIVE,
    INACTIVE
}
