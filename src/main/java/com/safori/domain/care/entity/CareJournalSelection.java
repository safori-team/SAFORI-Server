package com.safori.domain.care.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일지에서 고른 항목 하나. 항목 문구가 나중에 바뀌어도 일지는 작성 당시 문구({@code labelSnapshot})로 보인다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "care_journal_selection")
public class CareJournalSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "selection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cjs_journal"))
    private CareJournal journal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_code", nullable = false, foreignKey = @ForeignKey(name = "fk_cjs_option"))
    private JournalOption option;

    @Column(name = "label_snapshot", nullable = false, length = 50)
    private String labelSnapshot;

    /** 직접 입력 항목(기타)의 입력값. */
    @Column(name = "text_value", length = 200)
    private String text;

    static CareJournalSelection of(CareJournal journal, JournalOption option, String text) {
        return CareJournalSelection.builder()
                .journal(journal)
                .option(option)
                .labelSnapshot(option.getLabel())
                .text(text)
                .build();
    }
}
