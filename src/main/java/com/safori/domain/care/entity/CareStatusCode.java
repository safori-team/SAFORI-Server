package com.safori.domain.care.entity;

/**
 * 대상자 상태 코드(확인 필요도). 시스템이 사유를 감지해 정한다. 현재 기록이 없으면 표시 없음(X)이다.
 * 선언 순서가 낮은 → 높은 등급이다(비교는 {@link #isAtLeast}).
 */
public enum CareStatusCode {
    /** 관심 */
    INTEREST("관심 정보"),
    /** 주의 */
    CAUTION("확인 권장"),
    /** 즉시 확인 */
    URGENT("필요한 조치");

    /** 확인 사유 안내 문구 앞에 붙는 등급별 라벨. */
    private final String guidanceLabel;

    CareStatusCode(String guidanceLabel) {
        this.guidanceLabel = guidanceLabel;
    }

    public String guidanceLabel() {
        return guidanceLabel;
    }

    public boolean isAtLeast(CareStatusCode other) {
        return compareTo(other) >= 0;
    }
}
