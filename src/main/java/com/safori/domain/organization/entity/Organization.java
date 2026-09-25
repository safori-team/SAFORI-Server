package com.safori.domain.organization.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.organization.exception.OrganizationHandler;
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

import java.util.Objects;
import java.util.UUID;

/**
 * 어르신을 돌보는 기관. 역할·그룹·구성원·어르신·배정 등 백오피스의 모든 권한 데이터는 기관 안에 갇힌다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "organization",
        uniqueConstraints = @UniqueConstraint(name = "uq_org_public_id", columnNames = "public_id"))
public class Organization extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organization_id")
    private Long id;

    /** 외부 노출·토큰의 기관 컨텍스트 식별자. */
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private OrganizationStatus status;

    public static Organization create(String name) {
        return Organization.builder()
                .publicId(UUID.randomUUID().toString())
                .name(name)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    public boolean isActive() {
        return this.status == OrganizationStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = OrganizationStatus.INACTIVE;
    }

    public void activate() {
        this.status = OrganizationStatus.ACTIVE;
    }

    /**
     * 두 기관이 같은지 확인한다. 기관 A의 관리자가 기관 B의 구성원·역할·어르신을 다루지 못하게 하는 공통 경계다.
     * 지연 로딩 프록시를 초기화하지 않도록 식별자만 비교한다.
     */
    public static void requireSame(Organization expected, Organization actual) {
        if (expected == null || actual == null || !Objects.equals(expected.getId(), actual.getId())) {
            throw OrganizationHandler.MISMATCH;
        }
    }

    /** 구성원이 모두 기관 소속인지 확인한다. null은 SAFORI 운영(시스템) 처리로 보고 건너뛴다. */
    public static void requireMembers(Organization organization, OrganizationMember... members) {
        for (OrganizationMember member : members) {
            if (member != null) {
                requireSame(organization, member.getOrganization());
            }
        }
    }
}
