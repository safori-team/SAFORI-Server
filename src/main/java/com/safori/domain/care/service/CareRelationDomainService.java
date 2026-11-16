package com.safori.domain.care.service;

import com.safori.domain.care.entity.CareAssignment;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;

/**
 * 담당자·보호자의 데이터 범위를 만드는 관계(어르신 등록, 담당자 배정, 보호자 연결)를 관리한다.
 *
 * <p>어르신·담당자·보호자·처리자는 모두 같은 기관이어야 한다. 배정·연결 변경은 어르신 행을 잠가 직렬화하고,
 * 현재 배정·연결은 잠금 읽기로 최신 커밋을 본다. 어르신·기관·배정할 담당자·연결할 보호자는 인자로 받은
 * 인스턴스가 아니라 이 트랜잭션에서 다시 읽은 현재 상태로 판단한다.
 * actor 인자(assignedBy, linkedBy, endedBy)가 null이면 SAFORI 운영(시스템) 처리다.
 * 호출자 권한(ASSIGNMENT_MANAGE, GUARDIAN_LINK_MANAGE 등)은 UseCase의 {@code BackofficeAccessPolicy}가 검사한다.
 */
public interface CareRelationDomainService {

    /**
     * @param userId 어르신 앱 계정 {@code users.user_id}. 앱 가입 전이면 null.
     */
    CareRecipient registerRecipient(Organization organization, Long userId);

    /**
     * 담당자를 배정한다. 현재 배정이 있으면 종료하고 새로 배정한다(어르신 1명당 현재 담당자 1명).
     * 이미 같은 담당자가 배정돼 있으면 기존 배정을 그대로 돌려준다.
     *
     * <p>배정 범위(ASSIGNED_RECIPIENT) 권한을 가진 활성 구성원만 담당자가 될 수 있다.
     */
    CareAssignment assignWorker(CareRecipient recipient, OrganizationMember worker, OrganizationMember assignedBy,
                                String reason);

    /** 현재 배정을 종료한다. 배정이 없으면 아무것도 하지 않는다. */
    void endAssignment(CareRecipient recipient, OrganizationMember endedBy);

    /**
     * 보호자를 연결한다. 어르신 한 명에 보호자 여럿이 연결될 수 있고, 같은 보호자의 현재 연결이 있으면 그대로 돌려준다.
     *
     * <p>연결 범위(LINKED_RECIPIENT) 권한을 가진 활성 구성원만 보호자로 연결할 수 있다.
     */
    GuardianRecipientLink linkGuardian(CareRecipient recipient, OrganizationMember guardian,
                                       OrganizationMember linkedBy);

    void unlinkGuardian(CareRecipient recipient, OrganizationMember guardian, OrganizationMember endedBy);

    /** 구성원의 현재 배정·보호자 연결을 모두 종료한다(소속 종료 시). */
    void endAllRelationsOf(OrganizationMember member, OrganizationMember endedBy);
}
