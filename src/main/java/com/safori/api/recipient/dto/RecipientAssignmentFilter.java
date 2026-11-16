package com.safori.api.recipient.dto;

/** 대상자 목록의 배정 탭. */
public enum RecipientAssignmentFilter {
    ALL(null),
    ASSIGNED(true),
    UNASSIGNED(false);

    private final Boolean assigned;

    RecipientAssignmentFilter(Boolean assigned) {
        this.assigned = assigned;
    }

    /** null이면 배정 여부로 거르지 않는다. */
    public Boolean assigned() {
        return assigned;
    }
}
