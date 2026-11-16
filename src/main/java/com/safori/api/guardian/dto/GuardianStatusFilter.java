package com.safori.api.guardian.dto;

/** 보호자 목록 탭. */
public enum GuardianStatusFilter {
    ALL(null),
    LINKED(true),
    UNLINKED(false);

    private final Boolean linked;

    GuardianStatusFilter(Boolean linked) {
        this.linked = linked;
    }

    public Boolean linked() {
        return linked;
    }
}
