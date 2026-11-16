package com.safori.domain.care.entity;

import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기관이 관리하는 어르신. 담당자 배정과 보호자 연결의 기준이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "care_recipient",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_cr_public_id", columnNames = "public_id"),
                // 어르신은 한 기관에만 등록한다. 앱 가입 전(user_id NULL)은 여러 행이어도 된다.
                @UniqueConstraint(name = "uq_cr_user", columnNames = "user_id")
        })
public class CareRecipient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipient_id")
    private Long id;

    /** 외부 노출 식별자. URL 경로 변수에는 내부 ID 대신 이 값을 쓴다. */
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cr_organization"))
    private Organization organization;

    /**
     * 어르신 앱 계정({@code users.user_id}). 앱 가입 전이면 null.
     * 백오피스가 어르신 도메인 엔티티를 공유하지 않도록 연관관계 대신 식별자로만 참조한다.
     */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(16)")
    private CareRecipientStatus status;

    /**
     * 현재 기록(현황에 보이는 상태 코드·사유·처리 상태). null이면 상태 코드 X.
     * 목록을 한 번의 조인으로 읽기 위해 기록 이력과 별도로 가리킨다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_record_id", foreignKey = @ForeignKey(name = "fk_cr_current_record"))
    private CareRecord currentRecord;

    public static CareRecipient register(Organization organization, Long userId) {
        return CareRecipient.builder()
                .publicId(UUID.randomUUID().toString())
                .organization(organization)
                .userId(userId)
                .status(CareRecipientStatus.ACTIVE)
                .build();
    }

    /**
     * 새 기록을 받는다. 현재 기록이 없거나 새 기록 등급이 같거나 높으면 현재 기록을 덮어쓰고(기존은 흡수),
     * 낮으면 새 기록을 흡수한다. 흡수된 기록도 이력으로 남는다.
     */
    public void receive(CareRecord record) {
        if (currentRecord == null || record.getStatusCode().isAtLeast(currentRecord.getStatusCode())) {
            if (currentRecord != null) {
                currentRecord.absorb();
            }
            currentRecord = record;
        } else {
            record.absorb();
        }
    }

    /**
     * 현재 기록의 처리 상태를 바꾼다. 완료하면 현재 기록에서 빠져 상태 코드가 X가 된다.
     *
     * @return 현재 기록이 아니면(흡수·완료된 기록) false — 바꾸지 않는다
     */
    public boolean process(CareRecord record, CareProcessingStatus status, OrganizationMember by, LocalDateTime now) {
        if (currentRecord == null || !currentRecord.getId().equals(record.getId()) || !status.isManual()) {
            return false;
        }
        record.process(status, by, now);
        if (status == CareProcessingStatus.DONE) {
            currentRecord = null;
        }
        return true;
    }

    public boolean isActive() {
        return this.status == CareRecipientStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = CareRecipientStatus.INACTIVE;
    }

    public void activate() {
        this.status = CareRecipientStatus.ACTIVE;
    }
}
