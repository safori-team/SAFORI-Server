package com.safori.domain.access.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * SAFORI가 제공하는 기본 역할 구성의 버전별 기록. 기관 역할이 어느 템플릿·버전에서 만들어졌는지 추적하는 기준이다.
 * 기본 권한 목록은 {@link RoleTemplateCode}에 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "access_role_template",
        uniqueConstraints = @UniqueConstraint(name = "uq_art_code_version", columnNames = {"code", "version"}))
public class AccessRoleTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_template_id")
    private Long id;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 템플릿 구성 버전(낙관적 락 아님). */
    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, columnDefinition = "VARCHAR(32)")
    private DataScope dataScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private AccessStatus status;

    public static AccessRoleTemplate from(RoleTemplateCode template) {
        return AccessRoleTemplate.builder()
                .code(template.name())
                .name(template.getDisplayName())
                .version(template.getVersion())
                .dataScope(template.getDataScope())
                .status(AccessStatus.ACTIVE)
                .build();
    }
}
