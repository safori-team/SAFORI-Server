package com.safori.domain.organization.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Adaptor
@RequiredArgsConstructor
public class OrganizationMemberAdaptorImpl implements OrganizationMemberAdaptor {

    private final OrganizationMemberRepository organizationMemberRepository;

    @Override
    public Optional<OrganizationMember> findForAuthentication(String accountUuid, String organizationPublicId) {
        return organizationMemberRepository.findForAuthentication(accountUuid, organizationPublicId);
    }

    @Override
    public Optional<OrganizationMember> findForAuthorization(Long memberId) {
        return organizationMemberRepository.findForAuthorization(memberId);
    }
}
