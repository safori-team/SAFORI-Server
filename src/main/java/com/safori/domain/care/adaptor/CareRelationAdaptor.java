package com.safori.domain.care.adaptor;

import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.organization.entity.OrganizationMember;

/**
 * 담당자·보호자의 데이터 범위를 판정할 때 보는 현재 관계.
 */
public interface CareRelationAdaptor {

    /** 담당자가 어르신에게 현재 배정돼 있는지. */
    boolean existsActiveAssignment(CareRecipient recipient, OrganizationMember worker);

    /** 보호자가 어르신과 현재 연결돼 있는지. */
    boolean existsActiveGuardianLink(CareRecipient recipient, OrganizationMember guardian);
}
