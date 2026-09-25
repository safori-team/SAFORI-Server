package com.safori.domain.organization.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.service.AccessProvisioningDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DomainService
@RequiredArgsConstructor
public class OrganizationDomainServiceImpl implements OrganizationDomainService {

    private final OrganizationRepository organizationRepository;
    private final AccessProvisioningDomainService accessProvisioningDomainService;

    @Override
    public Organization create(String name) {
        Organization organization = organizationRepository.save(Organization.create(name));
        accessProvisioningDomainService.provisionDefaults(organization);
        return organization;
    }

    @Override
    public Organization deactivate(Organization organization) {
        Organization current = reload(organization);
        current.deactivate();
        return current;
    }

    @Override
    public Organization activate(Organization organization) {
        Organization current = reload(organization);
        current.activate();
        return current;
    }

    /** 다른 트랜잭션에서 읽은 인스턴스를 바꾸면 저장되지 않으므로, 이 트랜잭션에서 다시 읽은 기관을 바꾼다. */
    private Organization reload(Organization organization) {
        return organizationRepository.findById(organization.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 기관입니다: " + organization.getId()));
    }
}
