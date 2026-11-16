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
 * 담당자 배정 이력. {@code ended_at IS NULL}인 행이 현재 배정이다.
 *
 * <p>재배정은 기존 행을 종료하고 새 행을 만든다. 행을 덮어쓰지 않으므로 누가 언제 담당했는지가 남고,
 * 같은 담당자가 다시 배정돼도 배정 ID가 새로 생긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "care_assignment",
        indexes = {
                @Index(name = "idx_ca_worker_active",
                        columnList = "organization_id, worker_member_id, ended_at, recipient_id"),
                @Index(name = "idx_ca_recipient_active", columnList = "recipient_id, ended_at")
        })
public class CareAssignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ca_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ca_recipient"))
    private CareRecipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_member_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ca_worker"))
    private OrganizationMember worker;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 배정한 구성원. null이면 SAFORI 운영(시스템)이 배정했다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", foreignKey = @ForeignKey(name = "fk_ca_assigned_by"))
    private OrganizationMember assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ended_by", foreignKey = @ForeignKey(name = "fk_ca_ended_by"))
    private OrganizationMember endedBy;

    @Column(name = "reason")
    private String reason;

    public static CareAssignment start(CareRecipient recipient, OrganizationMember worker,
                                       OrganizationMember assignedBy, String reason, LocalDateTime now) {
        return CareAssignment.builder()
                .organization(recipient.getOrganization())
                .recipient(recipient)
                .worker(worker)
                .startedAt(now)
                .assignedBy(assignedBy)
                .reason(reason)
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

    public boolean isAssignedTo(OrganizationMember member) {
        return this.worker.isSameMember(member);
    }
}
