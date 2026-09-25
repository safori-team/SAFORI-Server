package com.safori.domain.care.entity;

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
                @UniqueConstraint(name = "uq_cr_org_user", columnNames = {"organization_id", "user_id"})
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

    public static CareRecipient register(Organization organization, Long userId) {
        return CareRecipient.builder()
                .publicId(UUID.randomUUID().toString())
                .organization(organization)
                .userId(userId)
                .status(CareRecipientStatus.ACTIVE)
                .build();
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
