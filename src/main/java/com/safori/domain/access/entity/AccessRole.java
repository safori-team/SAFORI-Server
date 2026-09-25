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

/**
 * 기관이 소유하는 역할. 권한 묶음({@code access_role_permission})과 데이터 범위를 갖는다.
 *
 * <p>기관 생성 시 기본 템플릿마다 하나씩 만들어지고, 이후 권한 구성은 기관별로 바뀔 수 있다.
 * 역할 코드는 판정에 쓰지 않는다 — 판정은 연결된 권한과 {@link #dataScope}로만 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "access_role",
        uniqueConstraints = @UniqueConstraint(name = "uq_ar_org_code", columnNames = {"organization_id", "code"}))
public class AccessRole extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ar_organization"))
    private Organization organization;

    /** 생성 기준 템플릿. 기관이 직접 만든 역할이면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_template_id", foreignKey = @ForeignKey(name = "fk_ar_source_template"))
    private AccessRoleTemplate sourceTemplate;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 이 역할로 받은 권한이 미치는 어르신 범위. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, columnDefinition = "VARCHAR(32)")
    private DataScope dataScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private AccessStatus status;

    public static AccessRole fromTemplate(Organization organization, AccessRoleTemplate template) {
        return AccessRole.builder()
                .organization(organization)
                .sourceTemplate(template)
                .code(template.getCode())
                .name(template.getName())
                .dataScope(template.getDataScope())
                .status(AccessStatus.ACTIVE)
                .build();
    }

    public static AccessRole custom(Organization organization, String code, String name, DataScope dataScope) {
        return AccessRole.builder()
                .organization(organization)
                .code(code)
                .name(name)
                .dataScope(dataScope)
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
