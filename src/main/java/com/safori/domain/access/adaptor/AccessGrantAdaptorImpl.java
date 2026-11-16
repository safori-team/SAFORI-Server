package com.safori.domain.access.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.access.policy.PermissionGrant;
import com.safori.domain.access.repository.AccessGroupMemberRepository;
import com.safori.domain.access.repository.AccessMemberRoleRepository;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Adaptor
@RequiredArgsConstructor
public class AccessGrantAdaptorImpl implements AccessGrantAdaptor {

    private final AccessGroupMemberRepository accessGroupMemberRepository;
    private final AccessMemberRoleRepository accessMemberRoleRepository;

    @Override
    public List<PermissionGrant> queryGrantsInheritedFromGroups(Long memberId, Long organizationId) {
        return accessGroupMemberRepository.findGrantsInheritedFromGroups(memberId, organizationId);
    }

    @Override
    public List<PermissionGrant> queryGrantsFromDirectRoles(Long memberId, Long organizationId, LocalDateTime now) {
        return accessMemberRoleRepository.findGrantsFromDirectRoles(memberId, organizationId, now);
    }
}
