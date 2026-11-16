package com.safori.domain.access.entity;

import com.safori.domain.organization.entity.Organization;
import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * 기관 구성원을 권한 관리 단위로 묶는 그룹. 그룹에 연결된 역할을 소속 구성원이 상속한다.
 *
 * <p>권한 판정은 그룹 코드가 아니라 그룹에 연결된 역할과 권한으로만 한다. {@link #systemCode}는
 * 기본 그룹을 찾기 위한 키일 뿐이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "access_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ag_group_uuid", columnNames = "group_uuid"),
                @UniqueConstraint(name = "uq_ag_org_system_code", columnNames = {"organization_id", "system_code"})
        })
public class AccessGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    /** 화면에 노출하는 안정적인 식별자. */
    @Column(name = "group_uuid", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String groupUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ag_organization"))
    private Organization organization;

    /** 기본 그룹의 코드({@link RoleTemplateCode} 이름). 기관이 만든 그룹은 null. */
    @Column(name = "system_code", updatable = false, length = 64)
    private String systemCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, columnDefinition = "VARCHAR(16)")
    private AccessGroupType groupType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private AccessStatus status;

    public static AccessGroup system(Organization organization, RoleTemplateCode template) {
        return AccessGroup.builder()
                .groupUuid(UUID.randomUUID().toString())
                .organization(organization)
                .systemCode(template.name())
                .name(template.getDisplayName())
                .groupType(AccessGroupType.SYSTEM)
                .status(AccessStatus.ACTIVE)
                .build();
    }

    public static AccessGroup custom(Organization organization, String name) {
        return AccessGroup.builder()
                .groupUuid(UUID.randomUUID().toString())
                .organization(organization)
                .name(name)
                .groupType(AccessGroupType.CUSTOM)
                .status(AccessStatus.ACTIVE)
                .build();
    }

    public boolean isActive() {
        return this.status == AccessStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = AccessStatus.INACTIVE;
    }

    public void activate() {
        this.status = AccessStatus.ACTIVE;
    }
}
