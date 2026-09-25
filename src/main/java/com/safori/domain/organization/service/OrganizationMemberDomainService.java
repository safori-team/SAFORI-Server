package com.safori.domain.organization.service;

import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;

/**
 * 기관 초대 → 승인으로 구성원을 받는다. 승인 전(PENDING)에는 기본 그룹에 들어가 있어도 권한이 없다.
 *
 * <p>상태를 바꾸거나 상태로 판단하는 대상(구성원·기관·계정)은 인자로 받은 인스턴스가 아니라 이 트랜잭션에서
 * 식별자로 다시 읽은 엔티티를 쓴다. 호출자가 다른 트랜잭션에서 읽은(준영속) 인스턴스를 넘겨도 변경이 저장되고,
 * 현재 상태는 반환값으로 받는다.
 *
 * <p>actor 인자(invitedBy, approver, revokedBy)가 null이면 SAFORI 운영(시스템) 처리다.
 * 최초 기관 관리자처럼 기관 안에 승인할 사람이 아직 없을 때 쓴다. 호출자 권한은 UseCase의
 * {@code BackofficeAccessPolicy}(MEMBER_MANAGE)가 검사한다.
 */
public interface OrganizationMemberDomainService {

    /** 계정을 기관에 초대한다. 선택한 기본 역할의 기본 그룹에 넣고 PENDING으로 둔다. */
    OrganizationMember invite(Organization organization, BackofficeAccount account, RoleTemplateCode initialRole,
                              OrganizationMember invitedBy);

    /** 가입 승인. 본인 승인과 다른 기관 구성원의 승인은 막는다. */
    OrganizationMember approve(OrganizationMember member, OrganizationMember approver);

    OrganizationMember suspend(OrganizationMember member);

    OrganizationMember reactivate(OrganizationMember member);

    /** 소속 종료. 현재 배정과 보호자 연결도 함께 종료한다. 작성 기록과 이력은 남긴다. */
    OrganizationMember revoke(OrganizationMember member, OrganizationMember revokedBy);
}
