package com.safori.domain.account.service;

import com.safori.domain.account.entity.BackofficeAccount;

/**
 * 상태를 바꾸는 메서드는 인자로 받은 계정을 식별자로만 쓰고, 이 트랜잭션에서 다시 읽은 계정을 바꿔 돌려준다.
 * 호출자가 다른 트랜잭션에서 읽은(준영속) 인스턴스를 넘겨도 변경이 저장된다. 현재 상태는 반환값으로 받는다.
 */
public interface BackofficeAccountDomainService {

    BackofficeAccount register(String loginId, String rawPassword, String name);

    /** 정지. 이미 발급된 백오피스 토큰도 다음 요청부터 거부된다. */
    BackofficeAccount suspend(BackofficeAccount account);

    BackofficeAccount activate(BackofficeAccount account);

    /** 계정 상태는 두고 발급된 토큰만 모두 무효화한다(강제 로그아웃). */
    BackofficeAccount revokeIssuedTokens(BackofficeAccount account);
}
