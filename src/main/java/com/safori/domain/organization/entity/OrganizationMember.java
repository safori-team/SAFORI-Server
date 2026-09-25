package com.safori.domain.organization.entity;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.organization.exception.OrganizationHandler;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 백오피스 계정의 기관별 소속. 권한 계산의 기준 단위다.
 *
 * <p>그룹 소속과 개인 역할은 계정이 아니라 이 멤버십에 붙는다. 그래서 한 계정이 여러 기관에 속해도
 * 기관 A에서 받은 역할이 기관 B의 권한 판정에 섞이지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "organization_member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_om_org_account", columnNames = {"organization_id", "account_id"})
        },
        indexes = {
                @Index(name = "idx_om_account", columnList = "account_id")
        })
public class OrganizationMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organization_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_om_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_om_account"))
    private BackofficeAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private OrganizationMemberStatus status;

    /** 초대한 구성원. null이면 SAFORI 운영(시스템)이 등록했다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", foreignKey = @ForeignKey(name = "fk_om_invited_by"))
    private OrganizationMember invitedBy;

    /** 가입을 승인한 구성원. null이면 SAFORI 운영(시스템)이 승인했다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_om_approved_by"))
    private OrganizationMember approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public static OrganizationMember invite(Organization organization, BackofficeAccount account,
                                            OrganizationMember invitedBy) {
        return OrganizationMember.builder()
                .organization(organization)
                .account(account)
                .status(OrganizationMemberStatus.PENDING)
                .invitedBy(invitedBy)
                .build();
    }

    public void approve(OrganizationMember approver, LocalDateTime now) {
        requireStatus(OrganizationMemberStatus.PENDING);
        this.status = OrganizationMemberStatus.ACTIVE;
        this.approvedBy = approver;
        this.approvedAt = now;
    }

    public void suspend() {
        requireStatus(OrganizationMemberStatus.ACTIVE);
        this.status = OrganizationMemberStatus.SUSPENDED;
    }

    public void reactivate() {
        requireStatus(OrganizationMemberStatus.SUSPENDED);
        this.status = OrganizationMemberStatus.ACTIVE;
    }

    public void revoke(LocalDateTime now) {
        if (this.status == OrganizationMemberStatus.REVOKED) {
            throw OrganizationHandler.MEMBER_INVALID_STATUS;
        }
        this.status = OrganizationMemberStatus.REVOKED;
        this.revokedAt = now;
    }

    public boolean isActive() {
        return this.status == OrganizationMemberStatus.ACTIVE;
    }

    /** 멤버십·계정·기관이 모두 활성이라 권한을 행사할 수 있는 상태인지. 하나라도 아니면 모든 권한 판정이 거부된다. */
    public boolean isActiveActor() {
        return isActive() && this.account.isActive() && this.organization.isActive();
    }

    public boolean isSameMember(OrganizationMember other) {
        return other != null && this.id != null && this.id.equals(other.getId());
    }

    private void requireStatus(OrganizationMemberStatus expected) {
        if (this.status != expected) {
            throw OrganizationHandler.MEMBER_INVALID_STATUS;
        }
    }
}
