package com.safori.security.dto;

/**
 * 검증을 통과한 백오피스 토큰의 클레임. 이 값만으로 인증하지 않고 {@code BackofficeActorResolver}가 DB 상태를 다시 본다.
 *
 * @param accountUuid          {@code backoffice_account.account_uuid} (subject)
 * @param organizationPublicId 로그인 시 선택한 기관 컨텍스트 {@code organization.public_id}
 * @param authVersion          발급 시점의 {@code backoffice_account.auth_version}
 * @param role                 발급 시점의 역할. 화면 선택용이며 인가에는 쓰지 않는다
 */
public record BackofficeTokenClaims(String accountUuid, String organizationPublicId, long authVersion,
                                    AccountRole role) {
}
