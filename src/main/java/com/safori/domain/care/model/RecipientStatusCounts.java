package com.safori.domain.care.model;

/**
 * 대상자 현황·목록 탭 개수. 조회 범위·검색어·담당자 필터를 적용하고, 상태 코드·배정 탭과는 무관하다.
 * 집계 대상이 0건이면 SUM이 null로 오므로 0으로 바꾼다.
 */
public record RecipientStatusCounts(long total, long assigned, long urgent, long caution, long interest) {

    public RecipientStatusCounts(Long total, Long assigned, Long urgent, Long caution, Long interest) {
        this(zero(total), zero(assigned), zero(urgent), zero(caution), zero(interest));
    }

    private static long zero(Long value) {
        return value == null ? 0 : value;
    }
}
