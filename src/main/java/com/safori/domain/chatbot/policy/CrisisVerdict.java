package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.entity.CrisisTrigger;

/**
 * 가드레일 판정 결과.
 *
 * @param detected 상담을 중단해야 하는지
 * @param trigger  중단시킨 신호 (detected=false면 null)
 * @param detail   로그·운영용 근거 문자열 (걸린 키워드, 차단 카테고리 등). 사용자에게 노출하지 않는다.
 */
public record CrisisVerdict(boolean detected, CrisisTrigger trigger, String detail) {

    private static final CrisisVerdict NONE = new CrisisVerdict(false, null, null);

    public static CrisisVerdict none() {
        return NONE;
    }

    public static CrisisVerdict of(CrisisTrigger trigger, String detail) {
        return new CrisisVerdict(true, trigger, detail);
    }
}
