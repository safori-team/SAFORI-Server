package com.safori.common.consts;

import lombok.experimental.UtilityClass;

/**
 * 로그인 계정 입력 규칙. 로그인 엔드포인트가 하나라 어르신 회원가입과 담당자·보호자 등록이 같은 규칙을 쓴다.
 */
@UtilityClass
public final class AccountStaticValues {

    /** 아이디: 영문·숫자만 ({@code @Size(min = 6, max = 12)}와 함께 쓴다). */
    public static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]+$";

    /** 비밀번호: 영문·숫자 혼합 ({@code @Size(min = 8, max = 20)}와 함께 쓴다). */
    public static final String PASSWORD_PATTERN = "^(?=.*[a-zA-Z])(?=.*\\d).+$";
}
