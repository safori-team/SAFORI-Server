package com.safori.domain.care.entity;

import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 보호자와 어르신의 연결 이력. {@code ended_at IS NULL}인 행이 현재 연결이다. 어르신 한 명에 보호자 여럿이 연결될 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "guardian_recipient_link",
        indexes = {
                @Index(name = "idx_grl_guardian_active",
                        columnList = "organization_id, guardian_member_id, ended_at, recipient_id"),
                @Index(name = "idx_grl_recipient_active", columnList = "recipient_id, ended_at")
        })
public class GuardianRecipientLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_grl_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_grl_recipient"))
    private CareRecipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_grl_guardian"))
    private OrganizationMember guardian;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 연결한 구성원. null이면 SAFORI 운영(시스템)이 연결했다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by", foreignKey = @ForeignKey(name = "fk_grl_linked_by"))
    private OrganizationMember linkedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by", foreignKey = @ForeignKey(name = "fk_grl_ended_by"))
    private OrganizationMember endedBy;

    public static GuardianRecipientLink start(CareRecipient recipient, OrganizationMember guardian,
                                              OrganizationMember linkedBy, LocalDateTime now) {
        return GuardianRecipientLink.builder()
                .organization(recipient.getOrganization())
                .recipient(recipient)
                .guardian(guardian)
                .startedAt(now)
                .linkedBy(linkedBy)
                .build();
    }

    public void end(OrganizationMember endedBy, LocalDateTime now) {
        if (this.endedAt == null) {
            this.endedAt = now;
            this.endedBy = endedBy;
        }
    }

    public boolean isActive() {
        return this.endedAt == null;
    }
}
