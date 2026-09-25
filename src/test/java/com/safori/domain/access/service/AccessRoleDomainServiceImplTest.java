package com.safori.domain.access.service;

import com.safori.domain.access.entity.AccessMemberRole;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.exception.AccessHandler;
import com.safori.domain.access.repository.AccessMemberRoleRepository;
import com.safori.domain.access.repository.AccessPermissionRepository;
import com.safori.domain.access.repository.AccessRolePermissionRepository;
import com.safori.domain.access.repository.AccessRoleRepository;
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

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.RAW_CONTENT_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccessRoleDomainServiceImplTest {

    @Mock AccessRoleRepository roleRepository;
    @Mock AccessPermissionRepository permissionRepository;
    @Mock AccessRolePermissionRepository rolePermissionRepository;
    @Mock AccessMemberRoleRepository memberRoleRepository;
    @InjectMocks AccessRoleDomainServiceImpl roleService;

    private final Organization organization = organization(1L);
    private final AccessRole workerRole = role(10L, organization);
    private final OrganizationMember admin = member(100L, organization);
    private final OrganizationMember worker = member(101L, organization);

    @Test
    @DisplayName("원문 열람처럼 기관이 부여할 수 없는 권한은 역할에 넣지 못한다")
    void nonAssignablePermissionIsRejected() {
        given(permissionRepository.findByCode("RAW_CONTENT_READ"))
                .willReturn(Optional.of(AccessPermission.from(RAW_CONTENT_READ)));

        assertThatThrownBy(() -> roleService.grantPermission(workerRole, RAW_CONTENT_READ))
                .isEqualTo(AccessHandler.PERMISSION_NOT_ASSIGNABLE);
        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 부여된 권한은 다시 저장하지 않는다")
    void grantPermissionIsIdempotent() {
        AccessPermission recipientRead = AccessPermission.from(RECIPIENT_READ);
        given(permissionRepository.findByCode("RECIPIENT_READ")).willReturn(Optional.of(recipientRead));
        given(rolePermissionRepository.existsByRoleAndPermission(workerRole, recipientRead)).willReturn(true);

        roleService.grantPermission(workerRole, RECIPIENT_READ);

        verify(rolePermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("권한을 빼면 역할-권한 연결을 지운다")
    void revokePermissionDeletesMapping() {
        AccessPermission recipientRead = AccessPermission.from(RECIPIENT_READ);
        AccessRolePermission mapping = AccessRolePermission.of(workerRole, recipientRead);
        given(permissionRepository.findByCode("RECIPIENT_READ")).willReturn(Optional.of(recipientRead));
        given(rolePermissionRepository.findByRoleAndPermission(workerRole, recipientRead)).willReturn(Optional.of(mapping));

        roleService.revokePermission(workerRole, RECIPIENT_READ);

        verify(rolePermissionRepository).delete(mapping);
    }

    @Test
    @DisplayName("커스텀 역할에 부여할 수 없는 권한이 섞여 있으면 역할 자체를 만들지 않는다")
    void customRoleWithNonAssignablePermissionIsNotCreated() {
        given(roleRepository.existsByOrganizationAndCode(organization, "RAW_VIEWER")).willReturn(false);
        given(permissionRepository.findByCode(anyString())).willAnswer(invocation ->
                PermissionCode.find(invocation.getArgument(0)).map(AccessPermission::from));

        assertThatThrownBy(() -> roleService.createCustomRole(
                organization, "RAW_VIEWER", "원문 열람", ORGANIZATION, EnumSet.of(RECIPIENT_READ, RAW_CONTENT_READ)))
                .isEqualTo(AccessHandler.PERMISSION_NOT_ASSIGNABLE);
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 기관에 같은 역할 코드는 만들 수 없다")
    void duplicateRoleCodeIsRejected() {
        given(roleRepository.existsByOrganizationAndCode(organization, "CARE_WORKER")).willReturn(true);

        assertThatThrownBy(() -> roleService.createCustomRole(
                organization, "CARE_WORKER", "담당자", ASSIGNED_RECIPIENT, Set.of()))
                .isEqualTo(AccessHandler.ROLE_CODE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("다른 기관 역할은 개인에게 부여할 수 없다")
    void foreignRoleCannotBeGranted() {
        AccessRole foreignRole = role(20L, organization(2L));

        assertThatThrownBy(() -> roleService.grantMemberRole(worker, foreignRole, admin, null, null))
                .isEqualTo(OrganizationHandler.MISMATCH);
        verifyNoInteractions(memberRoleRepository);
    }

    @Test
    @DisplayName("만료 시각이 현재 이전이면 부여하지 않는다")
    void pastExpiryIsRejected() {
        assertThatThrownBy(() -> roleService.grantMemberRole(
                worker, workerRole, admin, LocalDateTime.now().minusMinutes(1), null))
                .isEqualTo(AccessHandler.INVALID_ROLE_EXPIRY);
    }

    @Test
    @DisplayName("처음 부여하면 저장하고, 회수된 같은 역할을 다시 주면 그 행을 재부여한다")
    void grantThenRegrantAfterRevocation() {
        given(memberRoleRepository.findByMemberAndRole(worker, workerRole)).willReturn(Optional.empty());
        given(memberRoleRepository.save(any(AccessMemberRole.class))).willAnswer(invocation -> invocation.getArgument(0));

        AccessMemberRole granted = roleService.grantMemberRole(worker, workerRole, admin, null, "인수인계");
        assertThat(granted.getGrantedBy()).isEqualTo(admin);
        assertThat(granted.isEffectiveAt(LocalDateTime.now())).isTrue();

        granted.revoke(LocalDateTime.now());
        given(memberRoleRepository.findByMemberAndRole(worker, workerRole)).willReturn(Optional.of(granted));

        roleService.grantMemberRole(worker, workerRole, admin, null, "재부여");

        assertThat(granted.getRevokedAt()).isNull();
        assertThat(granted.getReason()).isEqualTo("재부여");
        verify(memberRoleRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("개인 역할을 회수하면 회수 시각이 남는다")
    void revokeMemberRoleRecordsRevocation() {
        AccessMemberRole grant = AccessMemberRole.grant(worker, workerRole, admin, null, null, LocalDateTime.now());
        given(memberRoleRepository.findByMemberAndRole(worker, workerRole)).willReturn(Optional.of(grant));

        roleService.revokeMemberRole(worker, workerRole);

        assertThat(grant.getRevokedAt()).isNotNull();
        assertThat(grant.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    private static Organization organization(Long id) {
        return Organization.builder()
                .id(id).publicId("organization-" + id).name("기관 " + id).status(OrganizationStatus.ACTIVE).build();
    }

    private static AccessRole role(Long id, Organization organization) {
        return AccessRole.builder()
                .id(id).organization(organization).code("CARE_WORKER").name("담당자")
                .dataScope(ASSIGNED_RECIPIENT).build();
    }

    private static OrganizationMember member(Long id, Organization organization) {
        return OrganizationMember.builder()
                .id(id).organization(organization).status(OrganizationMemberStatus.ACTIVE).build();
    }
}
