package com.safori.common.util;

/**
 * 휴대폰 번호. 입력은 하이픈이 있어도 없어도 되고, 저장은 숫자만(예: 01012345678) 한다.
 */
public final class PhoneNumber {

    /** 요청 DTO의 {@code @Pattern}에 쓰는 입력 형식. */
    public static final String PATTERN = "^01[016789]-?[0-9]{3,4}-?[0-9]{4}$";

    private PhoneNumber() {
    }

    /** 저장 형식(숫자만)으로 바꾼다. null이면 null. */
    public static String normalize(String phone) {
        return phone == null ? null : phone.replace("-", "");
    }
}
