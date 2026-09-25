package com.safori.domain.access.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * API가 검사하는 최소 행동 권한. {@link PermissionCode}를 코드 기준으로 동기화한 행이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "access_permission",
        uniqueConstraints = @UniqueConstraint(name = "uq_ap_code", columnNames = "code"))
public class AccessPermission extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long id;

    /** {@link PermissionCode#name()}. enum에서 사라진 코드가 남아 있어도 로딩이 깨지지 않도록 문자열로 둔다. */
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "description", nullable = false)
    private String description;

    /** false면 기관이 자기 역할에 추가할 수 없다(예: 원문 열람). */
    @Column(name = "organization_assignable", nullable = false)
    private boolean organizationAssignable;

    public static AccessPermission from(PermissionCode permission) {
        return AccessPermission.builder()
                .code(permission.name())
                .description(permission.getDescription())
                .organizationAssignable(permission.isOrganizationAssignable())
                .build();
    }

    /** 코드 카탈로그의 설명·부여 가능 여부를 반영한다. */
    public void syncWith(PermissionCode permission) {
        this.description = permission.getDescription();
        this.organizationAssignable = permission.isOrganizationAssignable();
    }
}
