package com.safori.domain.organization.model;

/** 보호자 목록 탭 개수. 보호자가 없으면 SUM이 null이라 0으로 받는다. */
public record GuardianCounts(long total, long linked) {

    public GuardianCounts(Long total, Long linked) {
        this(total == null ? 0 : total, linked == null ? 0 : linked);
    }
}
