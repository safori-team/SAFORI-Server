package com.safori.domain.organization.adaptor;

import com.safori.domain.organization.entity.OrganizationMember;

import java.util.Optional;

public interface OrganizationMemberAdaptor {

    /** 토큰이 가리키는 기관 구성원. 계정·기관을 함께 읽는다. 없으면 empty — 인증 실패로 처리한다. */
    Optional<OrganizationMember> findForAuthentication(String accountUuid, String organizationPublicId);

    /** 권한 판정용 조회. 계정·기관을 함께 읽는다. 없으면 empty — 거부로 처리한다. */
    Optional<OrganizationMember> findForAuthorization(Long memberId);
}
