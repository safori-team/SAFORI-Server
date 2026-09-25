package com.safori.domain.care.entity;

/** 기록의 처리 상태. 담당자가 미확인 → 진행 중 → 완료로 바꾼다. */
public enum CareProcessingStatus {
    /** 미확인 */
    UNCHECKED,
    /** 진행 중(조치중) */
    IN_PROGRESS,
    /** 완료. 대상자의 현재 기록에서 빠져 상태 코드가 X가 된다. */
    DONE,
    /**
     * 흡수됨(내부 값, 화면 비노출). 현재 기록보다 낮은 등급으로 들어왔거나, 더 높은 기록에 현재 기록 자리를 넘긴 기록.
     * 지우지 않고 이력으로 남긴다.
     */
    ABSORBED;

    /** 담당자가 직접 바꿀 수 있는 값인지. */
    public boolean isManual() {
        return this != ABSORBED;
    }
}
