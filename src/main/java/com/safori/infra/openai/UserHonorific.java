package com.safori.infra.openai;

import com.safori.domain.user.entity.Gender;

/**
 * AI 리포트에서 사용자를 지칭할 호칭을 만든다.
 * 고연령자 대상 서비스라 성별에 따라 할아버지/할머니로 지칭한다.
 * 성별 미상(null)이면 기본 "{이름}님"으로 폴백한다.
 */
final class UserHonorific {

    private UserHonorific() {
    }

    static String of(String name, Gender gender) {
        if (gender == Gender.MALE) return name + " 할아버지";
        if (gender == Gender.FEMALE) return name + " 할머니";
        return name + "님";
    }
}
