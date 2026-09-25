package com.safori.domain.access.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessGroupMember;
import com.safori.domain.access.entity.AccessGroupRole;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.repository.AccessGroupMemberRepository;
import com.safori.domain.access.repository.AccessGroupRepository;
import com.safori.domain.access.repository.AccessGroupRoleRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DomainService
@RequiredArgsConstructor
public class AccessGroupDomainServiceImpl implements AccessGroupDomainService {

    private final AccessGroupRepository groupRepository;
    private final AccessGroupMemberRepository groupMemberRepository;
    private final AccessGroupRoleRepository groupRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public AccessGroup getSystemGroup(Organization organization, RoleTemplateCode template) {
        return groupRepository.findByOrganizationAndSystemCode(organization, template.name())
                .orElseThrow(() -> new IllegalStateException("기본 그룹이 준비되지 않은 기관입니다: " + template));
    }

    @Override
    public AccessGroup createCustomGroup(Organization organization, String name) {
        return groupRepository.save(AccessGroup.custom(organization, name));
    }

    @Override
    public void addMember(AccessGroup group, OrganizationMember member) {
        Organization.requireSame(group.getOrganization(), member.getOrganization());
        if (!groupMemberRepository.existsByGroupAndMember(group, member)) {
            groupMemberRepository.save(AccessGroupMember.of(group, member));
        }
    }

    @Override
    public void removeMember(AccessGroup group, OrganizationMember member) {
        groupMemberRepository.findByGroupAndMember(group, member)
                .ifPresent(groupMemberRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCurrentMember(AccessGroup group) {
        return groupMemberRepository.existsByGroupAndMember_StatusNot(group, OrganizationMemberStatus.REVOKED);
    }

    @Override
    public void assignRole(AccessGroup group, AccessRole role) {
        Organization.requireSame(group.getOrganization(), role.getOrganization());
        if (!groupRoleRepository.existsByGroupAndRole(group, role)) {
            groupRoleRepository.save(AccessGroupRole.of(group, role));
        }
    }

    @Override
    public void unassignRole(AccessGroup group, AccessRole role) {
        groupRoleRepository.findByGroupAndRole(group, role)
                .ifPresent(groupRoleRepository::delete);
    }
}
