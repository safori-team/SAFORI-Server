package com.safori.domain.organization.service;

import com.safori.domain.access.service.AccessProvisioningDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationStatus;
import com.safori.domain.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrganizationDomainServiceImplTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock AccessProvisioningDomainService accessProvisioningDomainService;
    @InjectMocks OrganizationDomainServiceImpl organizationService;

    @Test
    @DisplayName("기관을 만들면 활성 상태로 저장하고 기본 역할·그룹을 준비한다")
    void createProvisionsDefaultAccess() {
        given(organizationRepository.save(any(Organization.class))).willAnswer(invocation -> invocation.getArgument(0));

        Organization organization = organizationService.create("사포리 복지관");

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(organization.getPublicId()).isNotBlank();
        verify(accessProvisioningDomainService).provisionDefaults(organization);
    }

    @Test
    @DisplayName("비활성화·재활성화는 넘겨받은 인스턴스가 아니라 다시 읽은 기관을 바꿔 돌려준다")
    void deactivateAndActivateChangeReloadedOrganization() {
        Organization stored = organization(1L);
        given(organizationRepository.findById(1L)).willReturn(Optional.of(stored));

        assertThat(organizationService.deactivate(organization(1L))).isSameAs(stored);
        assertThat(stored.isActive()).isFalse();

        organizationService.activate(organization(1L));
        assertThat(stored.isActive()).isTrue();
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id).publicId("organization-" + id).name("사포리 복지관").status(OrganizationStatus.ACTIVE).build();
    }
}
