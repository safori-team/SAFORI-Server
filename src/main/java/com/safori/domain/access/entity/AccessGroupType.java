package com.safori.domain.access.entity;

public enum AccessGroupType {

    /** 기관 생성 시 기본 역할 템플릿마다 만드는 그룹. {@code system_code}가 템플릿 코드다. */
    SYSTEM,

    /** 기관이 직접 만든 그룹. {@code system_code}는 null이다. */
    CUSTOM
}
