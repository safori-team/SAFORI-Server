package com.safori.domain.care.entity;

/**
 * 대상자 상태 코드. 시스템이 사유를 감지해 정한다. 현재 기록이 없으면 표시 없음(X)이다.
 * 선언 순서가 낮은 → 높은 등급이다(비교는 {@link #isAtLeast}).
 */
public enum CareStatusCode {
    /** 관심 */
    INTEREST,
    /** 주의 */
    CAUTION,
    /** 즉시 확인 */
    URGENT;

    public boolean isAtLeast(CareStatusCode other) {
        return compareTo(other) >= 0;
    }
}
