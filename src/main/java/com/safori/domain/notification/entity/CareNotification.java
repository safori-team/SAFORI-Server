package com.safori.domain.notification.entity;

import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.care.entity.CareRecord;
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
 * 복지관 알림 발송 이력. 받는 계정마다 한 행이고, 보낸 시각은 {@code created_date}다.
 * 같은 기록·종류·계정으로는 한 번만 보낸다(유니크 제약) — 전송 전에 행을 먼저 만들어 중복 발송을 막는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "care_notification",
        uniqueConstraints = @UniqueConstraint(name = "uq_cn_record_type_account",
                columnNames = {"record_id", "type", "account_id"}))
public class CareNotification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    /** 알림의 원인이 된 기록. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cn_record"))
    private CareRecord record;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(32)")
    private CareNotificationType type;

    /** 받는 계정(관리자·담당자·보호자). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cn_account"))
    private BackofficeAccount account;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    /** 전송에 성공한 기기 수. 성공·실패가 모두 0이면 등록된 기기가 없었던 것이다. */
    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    public static CareNotification of(CareRecord record, CareNotificationType type, BackofficeAccount account,
                                      String title, String body) {
        return CareNotification.builder()
                .record(record).type(type).account(account).title(title).body(body)
                .build();
    }

    public void sent(int successCount, int failureCount) {
        this.successCount = successCount;
        this.failureCount = failureCount;
    }
}
