package com.safori.domain.access.service;

import com.safori.domain.access.PhotoPermissionMatrix;
import com.safori.domain.access.entity.AccessGroup;
import com.safori.domain.access.entity.AccessPermission;
import com.safori.domain.access.entity.AccessRole;
import com.safori.domain.access.entity.AccessRolePermission;
import com.safori.domain.access.entity.AccessRoleTemplate;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.model.AccessCatalog;
import com.safori.domain.access.repository.AccessGroupRepository;
import com.safori.domain.access.repository.AccessGroupRoleRepository;
import com.safori.domain.access.repository.AccessPermissionRepository;
import com.safori.domain.access.repository.AccessRolePermissionRepository;
import com.safori.domain.access.repository.AccessRoleRepository;
import com.safori.domain.access.repository.AccessRoleTemplateRepository;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.PermissionCode.RAW_CONTENT_READ;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessProvisioningDomainServiceImplTest {

    @Mock AccessPermissionRepository permissionRepository;
    @Mock AccessRoleTemplateRepository templateRepository;
    @Mock AccessRoleRepository roleRepository;
    @Mock AccessRolePermissionRepository rolePermissionRepository;
    @Mock AccessGroupRepository groupRepository;
    @Mock AccessGroupRoleRepository groupRoleRepository;
    @InjectMocks AccessProvisioningDomainServiceImpl provisioningService;

    @Captor ArgumentCaptor<AccessPermission> permissionCaptor;
    @Captor ArgumentCaptor<AccessRolePermission> rolePermissionCaptor;
    @Captor ArgumentCaptor<AccessGroup> groupCaptor;

    private final Organization organization = Organization.builder()
            .id(1L).publicId("organization-1").name("사포리 복지관").status(OrganizationStatus.ACTIVE).build();

    @Test
    @DisplayName("카탈로그가 비어 있으면 권한 코드와 기본 역할 템플릿을 모두 만든다")
    void synchronizeCatalogCreatesMissingRows() {
        givenEmptyCatalog();

        AccessCatalog catalog = provisioningService.synchronizeCatalog();

        verify(permissionRepository, times(PermissionCode.values().length)).save(permissionCaptor.capture());
        assertThat(permissionCaptor.getAllValues())
                .extracting(AccessPermission::getCode)
                .containsExactlyInAnyOrder(Arrays.stream(PermissionCode.values()).map(Enum::name).toArray(String[]::new));
        assertThat(catalog.permission(RAW_CONTENT_READ).isOrganizationAssignable()).isFalse();
        assertThat(catalog.templates()).containsOnlyKeys(RoleTemplateCode.values());
    }

    @Test
    @DisplayName("이미 있는 권한은 다시 만들지 않고 설명·부여 가능 여부만 코드에 맞춘다")
    void synchronizeCatalogUpdatesExistingRows() {
        AccessPermission stale = AccessPermission.builder()
                .code("RAW_CONTENT_READ").description("옛 설명").organizationAssignable(true).build();
        given(permissionRepository.findByCode(anyString())).willAnswer(invocation ->
                "RAW_CONTENT_READ".equals(invocation.getArgument(0)) ? Optional.of(stale) : Optional.empty());
        given(permissionRepository.save(any(AccessPermission.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(templateRepository.findByCodeAndVersion(anyString(), anyLong())).willReturn(Optional.empty());
        given(templateRepository.save(any(AccessRoleTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));

        provisioningService.synchronizeCatalog();

        assertThat(stale.getDescription()).isEqualTo(RAW_CONTENT_READ.getDescription());
        assertThat(stale.isOrganizationAssignable()).isFalse();
        verify(permissionRepository, never()).save(stale);
    }

    @Test
    @DisplayName("기관 기본 구성: 템플릿마다 역할·기본 그룹을 만들고, 역할에 넣는 권한은 권한표와 같다")
    void provisionDefaultsCreatesPhotoRoles() {
        givenEmptyCatalog();
        givenNoRolesAndGroups();

        provisioningService.provisionDefaults(organization);

        verify(rolePermissionRepository, atLeastOnce()).save(rolePermissionCaptor.capture());
        Map<String, Set<String>> permissionsByRole = rolePermissionCaptor.getAllValues().stream()
                .collect(groupingBy(rolePermission -> rolePermission.getRole().getCode(),
                        mapping(rolePermission -> rolePermission.getPermission().getCode(), toSet())));
        for (RoleTemplateCode template : RoleTemplateCode.values()) {
            assertThat(permissionsByRole.get(template.name())).as(template.name())
                    .containsExactlyInAnyOrderElementsOf(PhotoPermissionMatrix.allowedPermissions(template).stream()
                            .map(Enum::name).toList());
        }

        verify(groupRepository, times(RoleTemplateCode.values().length)).save(groupCaptor.capture());
        assertThat(groupCaptor.getAllValues())
                .extracting(AccessGroup::getSystemCode)
                .containsExactlyInAnyOrder("ORG_ADMIN", "CARE_WORKER", "GUARDIAN");
        verify(groupRoleRepository, times(RoleTemplateCode.values().length)).save(any());
    }

    @Test
    @DisplayName("이미 있는 기본 역할에는 권한을 다시 넣지 않는다 — 기관이 바꾼 구성을 덮어쓰지 않는다")
    void provisionDefaultsKeepsExistingRoles() {
        givenEmptyCatalog();
        AccessRole customizedWorkerRole = AccessRole.custom(organization, "CARE_WORKER", "담당자", ASSIGNED_RECIPIENT);
        given(roleRepository.findByOrganizationAndCode(eq(organization), anyString())).willAnswer(invocation ->
                "CARE_WORKER".equals(invocation.getArgument(1)) ? Optional.of(customizedWorkerRole) : Optional.empty());
        given(roleRepository.save(any(AccessRole.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(groupRepository.findByOrganizationAndSystemCode(eq(organization), anyString())).willReturn(Optional.empty());
        given(groupRepository.save(any(AccessGroup.class))).willAnswer(invocation -> invocation.getArgument(0));

        provisioningService.provisionDefaults(organization);

        verify(rolePermissionRepository, atLeastOnce()).save(rolePermissionCaptor.capture());
        assertThat(rolePermissionCaptor.getAllValues())
                .noneMatch(rolePermission -> rolePermission.getRole() == customizedWorkerRole);
    }

    private void givenEmptyCatalog() {
        given(permissionRepository.findByCode(anyString())).willReturn(Optional.empty());
        given(permissionRepository.save(any(AccessPermission.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(templateRepository.findByCodeAndVersion(anyString(), anyLong())).willReturn(Optional.empty());
        given(templateRepository.save(any(AccessRoleTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenNoRolesAndGroups() {
        given(roleRepository.findByOrganizationAndCode(eq(organization), anyString())).willReturn(Optional.empty());
        given(roleRepository.save(any(AccessRole.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(groupRepository.findByOrganizationAndSystemCode(eq(organization), anyString())).willReturn(Optional.empty());
        given(groupRepository.save(any(AccessGroup.class))).willAnswer(invocation -> invocation.getArgument(0));
    }
}
