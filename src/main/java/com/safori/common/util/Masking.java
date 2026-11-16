package com.safori.common.util;

import java.time.LocalDate;

/**
 * 기관에 등록하기 전 어르신 조회처럼 본인 확인용으로만 보여줄 개인정보 마스킹. null이면 null.
 */
public final class Masking {

    private Masking() {
    }

    /** 김철수 → 김*수, 남궁민수 → 남**수, 김철 → 김*. */
    public static String name(String name) {
        if (name == null || name.length() < 2) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** 01012345678 → 010-****-5678 (숫자만 저장된 번호 기준). */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 8) {
            return phone;
        }
        return phone.substring(0, 3) + "-" + "*".repeat(phone.length() - 7) + "-"
                + phone.substring(phone.length() - 4);
    }

    /** 1960-03-12 → 1960-**-** (출생 연도만). */
    public static String birthDate(LocalDate birthDate) {
        return birthDate == null ? null : birthDate.getYear() + "-**-**";
    }
}
