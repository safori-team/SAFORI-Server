package com.safori.domain.access.entity;

import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 구성원에게 그룹을 거치지 않고 직접 부여한 역할(개인 예외). PK는 (organization_member_id, role_id).
 *
 * <p>회수·만료돼도 행을 지우지 않는다. 같은 역할을 다시 주면 이 행을 재부여한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@IdClass(AccessMemberRole.Key.class)
@Table(name = "access_member_role",
        indexes = @Index(name = "idx_amr_role", columnList = "role_id"))
public class AccessMemberRole extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_amr_member"))
    private OrganizationMember member;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_amr_role"))
    private AccessRole role;

    /** 부여한 구성원. null이면 SAFORI 운영(시스템)이 부여했다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by", foreignKey = @ForeignKey(name = "fk_amr_granted_by"))
    private OrganizationMember grantedBy;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /** null이면 회수 전까지 유효하다. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "reason")
    private String reason;

    public static AccessMemberRole grant(OrganizationMember member, AccessRole role, OrganizationMember grantedBy,
                                         LocalDateTime expiresAt, String reason, LocalDateTime now) {
        return AccessMemberRole.builder()
                .member(member)
                .role(role)
                .grantedBy(grantedBy)
                .grantedAt(now)
                .expiresAt(expiresAt)
                .reason(reason)
                .build();
    }

    /** 회수·만료된 부여를 다시 유효하게 만들거나, 유효한 부여의 기간·사유를 갱신한다. */
    public void regrant(OrganizationMember grantedBy, LocalDateTime expiresAt, String reason, LocalDateTime now) {
        this.grantedBy = grantedBy;
        this.grantedAt = now;
        this.expiresAt = expiresAt;
        this.reason = reason;
        this.revokedAt = null;
    }

    public void revoke(LocalDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isEffectiveAt(LocalDateTime now) {
        return this.revokedAt == null && (this.expiresAt == null || this.expiresAt.isAfter(now));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long member;
        private Long role;
    }
}
