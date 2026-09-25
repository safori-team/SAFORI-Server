package com.safori.domain.care.entity;

public enum CareRecipientStatus {
    ACTIVE,

    /** 기관 관리 종료. 백오피스 조회·배정 대상에서 빠진다. */
    INACTIVE
}
