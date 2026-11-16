package com.safori.domain.care.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.organization.entity.OrganizationMember;
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
import java.util.UUID;

/**
 * 대상자 기록. 시스템이 확인 사유를 감지할 때마다 한 건씩 쌓인다(지우지 않는다).
 * 대상자의 현재 기록({@code care_recipient.current_record_id})만 현황에 보이고 담당자가 처리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "care_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_crd_public_id", columnNames = "public_id"),
        indexes = @Index(name = "idx_crd_recipient_detected", columnList = "recipient_id, detected_at"))
public class CareRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long id;

    /** 외부 노출 식별자. 경로의 {@code recordId}. */
    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_crd_recipient"))
    private CareRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false, columnDefinition = "VARCHAR(16)")
    private CareStatusCode statusCode;

    /** 사유 종류. 제목·안내 문구는 종류로 정해진다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, columnDefinition = "VARCHAR(32)")
    private CareReasonType reasonType;

    /** 사유 설명 문장(숫자·감정이 들어간 부분). 현황 카드와 확인 사유에 보인다. */
    @Column(name = "reason_message", nullable = false)
    private String reasonMessage;

    /** 요청·감지 시각. */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, columnDefinition = "VARCHAR(16)")
    private CareProcessingStatus processingStatus;

    /** 마지막으로 처리 상태를 바꾼 구성원. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by", foreignKey = @ForeignKey(name = "fk_crd_processed_by"))
    private OrganizationMember processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static CareRecord detect(CareRecipient recipient, CareReasonType reasonType, String reasonMessage,
                                    LocalDateTime detectedAt) {
        return CareRecord.builder()
                .publicId(UUID.randomUUID().toString())
                .recipient(recipient)
                .statusCode(reasonType.statusCode())
                .reasonType(reasonType)
                .reasonMessage(reasonMessage)
                .detectedAt(detectedAt)
                .processingStatus(CareProcessingStatus.UNCHECKED)
                .build();
    }

    void absorb() {
        this.processingStatus = CareProcessingStatus.ABSORBED;
    }

    void process(CareProcessingStatus status, OrganizationMember by, LocalDateTime now) {
        this.processingStatus = status;
        this.processedBy = by;
        this.processedAt = now;
    }
}
