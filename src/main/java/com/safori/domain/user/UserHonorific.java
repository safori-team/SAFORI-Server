package com.safori.domain.user;

import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.entity.User;

/**
 * 사용자를 지칭할 호칭을 만든다. 고연령자 대상 서비스라 성별에 따라 할아버지/할머니로 지칭한다.
 * 성별 미상(null)이면 "{이름}님"으로 폴백한다.
 *
 * <p>부를 이름은 별명(nickname)을 우선하고, 없으면 실명(name)을 쓴다.
 *
 * <p>AI 리포트와 상담 챗봇(도란이)이 공유하는 단일 호칭 정책.
 */
public final class UserHonorific {

    private UserHonorific() {
    }

    /** 부를 이름 — 별명 우선, 없으면 실명, 둘 다 없으면 "내담자". */
    public static String displayName(User user) {
        if (user == null) return "내담자";
        return resolveName(user.getNickname(), user.getName());
    }

    /** User로부터 호칭 생성 (별명 우선). */
    public static String of(User user) {
        if (user == null) return "내담자";
        return build(displayName(user), user.getGender());
    }

    /** 이름 + 성별로 호칭 생성. 별명 우선 규칙이 필요 없는(이미 부를 이름이 정해진) 경우 사용. */
    public static String of(String name, Gender gender) {
        return build(resolveName(null, name), gender);
    }

    private static String resolveName(String nickname, String name) {
        if (nickname != null && !nickname.isBlank()) return nickname;
        if (name != null && !name.isBlank()) return name;
        return "내담자";
    }

    private static String build(String base, Gender gender) {
        if (gender == Gender.MALE) return base + " 할아버지";
        if (gender == Gender.FEMALE) return base + " 할머니";
        return base + "님";
    }
}
