package com.safori.security.dto;

import com.safori.domain.access.entity.RoleTemplateCode;

/**
 * 로그인한 주체의 역할. 로그인 응답·토큰·내 정보 조회에 담아 클라이언트가 화면을 고르는 데 쓴다.
 * 인가 판정에는 쓰지 않는다(백오피스는 요청마다 DB에서 계산한 권한으로 판정).
 */
public enum AccountRole {
    ELDER,
    ORG_ADMIN,
    CARE_WORKER,
    GUARDIAN;

    public static AccountRole of(RoleTemplateCode template) {
        return valueOf(template.name());
    }
}
