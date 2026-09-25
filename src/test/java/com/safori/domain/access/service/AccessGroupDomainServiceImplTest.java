package com.safori.domain.access.service;

import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupMember;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.repository.AccessGroupMemberRepository;
import com.safori.domain.access.repository.AccessGroupRepository;
import com.safori.domain.access.repository.AccessGroupRoleRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.entity.OrganizationStatus;
import com.safori.domain.organization.exception.OrganizationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccessGroupDomainServiceImplTest {

    @Mock AccessGroupRepository groupRepository;
    @Mock AccessGroupMemberRepository groupMemberRepository;
    @Mock AccessGroupRoleRepository groupRoleRepository;
    @InjectMocks AccessGroupDomainServiceImpl groupService;

    private final Organization organization = organization(1L);
    private final Organization foreignOrganization = organization(2L);
    private final AccessGroup group = AccessGroup.custom(organization, "팀장");

    @Test
    @DisplayName("그룹과 다른 기관의 구성원은 넣을 수 없다")
    void foreignMemberCannotJoin() {
        assertThatThrownBy(() -> groupService.addMember(group, member(foreignOrganization)))
                .isEqualTo(OrganizationHandler.MISMATCH);
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    @DisplayName("새 구성원은 저장하고, 이미 소속된 구성원은 다시 저장하지 않는다")
    void addMemberIsIdempotent() {
        OrganizationMember newcomer = member(organization);
        OrganizationMember existing = member(organization);
        given(groupMemberRepository.existsByGroupAndMember(group, newcomer)).willReturn(false);
        given(groupMemberRepository.existsByGroupAndMember(group, existing)).willReturn(true);

        groupService.addMember(group, newcomer);
        groupService.addMember(group, existing);

        verify(groupMemberRepository).save(any(AccessGroupMember.class));
    }

    @Test
    @DisplayName("그룹과 다른 기관의 역할은 연결할 수 없다")
    void foreignRoleCannotBeAssigned() {
        AccessRole foreignRole = AccessRole.custom(foreignOrganization, "ORG_ADMIN", "기관 관리자", ORGANIZATION);

        assertThatThrownBy(() -> groupService.assignRole(group, foreignRole))
                .isEqualTo(OrganizationHandler.MISMATCH);
        verify(groupRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("기본 그룹이 없는 기관은 준비되지 않은 상태로 본다")
    void missingSystemGroupIsIllegalState() {
        given(groupRepository.findByOrganizationAndSystemCode(organization, "CARE_WORKER")).willReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getSystemGroup(organization, CARE_WORKER))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id).publicId("organization-" + id).name("기관 " + id).status(OrganizationStatus.ACTIVE).build();
    }

    private static OrganizationMember member(Organization organization) {
        return OrganizationMember.builder().organization(organization).status(OrganizationMemberStatus.ACTIVE).build();
    }
}
