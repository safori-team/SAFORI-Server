package com.safori.domain.care.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareAssignmentRepository;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;

@Adaptor
@RequiredArgsConstructor
public class CareRelationAdaptorImpl implements CareRelationAdaptor {

    private final CareAssignmentRepository careAssignmentRepository;
    private final GuardianRecipientLinkRepository guardianRecipientLinkRepository;

    @Override
    public boolean existsActiveAssignment(CareRecipient recipient, OrganizationMember worker) {
        return careAssignmentRepository.existsByRecipientAndWorkerAndEndedAtIsNull(recipient, worker);
    }

    @Override
    public boolean existsActiveGuardianLink(CareRecipient recipient, OrganizationMember guardian) {
        return guardianRecipientLinkRepository.existsByRecipientAndGuardianAndEndedAtIsNull(recipient, guardian);
    }
}
