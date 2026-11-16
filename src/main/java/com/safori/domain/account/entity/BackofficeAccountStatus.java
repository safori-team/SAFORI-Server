package com.safori.domain.account.entity;

/**
 * 백오피스 로그인 계정 상태. 기관 소속·승인 상태는 계정이 아니라 {@code organization_member}가 관리한다.
 */
public enum BackofficeAccountStatus {
    ACTIVE,

    /** 로그인·권한 행사 불가. 정지하면 auth_version이 올라가 이미 발급된 토큰도 무효가 된다. */
    SUSPENDED
}
