package com.safori.domain.care.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.organization.entity.OrganizationMember;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 담당자 일지(최근 조치 기록). 작성일시는 {@code createdDate}(시스템), 확인 일시는 담당자가 입력한 {@code confirmedAt}.
 * 작성 당시 확인 필요도·처리 상태를 스냅샷으로 남긴다(보호자에게는 "일지 썼을 당시 상태"로 보인다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "care_journal",
        uniqueConstraints = @UniqueConstraint(name = "uq_cj_public_id", columnNames = "public_id"),
        indexes = @Index(name = "idx_cj_recipient_confirmed", columnList = "recipient_id, confirmed_at"))
public class CareJournal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_id")
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cj_recipient"))
    private CareRecipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "writer_member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cj_writer"))
    private OrganizationMember writer;

    /** 확인 일시(담당자 입력). 최근 안부 확인 날짜로 쓴다. */
    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    /** 작성 당시 현재 기록. 없었으면(상태 코드 X) null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", foreignKey = @ForeignKey(name = "fk_cj_record"))
    private CareRecord record;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code_snapshot", columnDefinition = "VARCHAR(16)")
    private CareStatusCode statusCodeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status_snapshot", columnDefinition = "VARCHAR(16)")
    private CareProcessingStatus processingStatusSnapshot;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    /** 보호자 공개 여부. 공개한 일지만 보호자에게 보인다. */
    @Column(name = "guardian_visible", nullable = false)
    private boolean guardianVisible;

    @Builder.Default
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CareJournalSelection> selections = new ArrayList<>();

    public static CareJournal write(CareRecipient recipient, OrganizationMember writer, LocalDateTime confirmedAt,
                                    String memo, boolean guardianVisible) {
        CareRecord current = recipient.getCurrentRecord();
        return CareJournal.builder()
                .publicId(UUID.randomUUID().toString())
                .recipient(recipient)
                .writer(writer)
                .confirmedAt(confirmedAt)
                .record(current)
                .statusCodeSnapshot(current == null ? null : current.getStatusCode())
                .processingStatusSnapshot(current == null ? null : current.getProcessingStatus())
                .memo(memo)
                .guardianVisible(guardianVisible)
                .build();
    }

    public void select(JournalOption option, String text) {
        selections.add(CareJournalSelection.of(this, option, text));
    }
}
