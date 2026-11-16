package com.safori.domain.care.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일지 폼 항목. {@code parent}로 하위 항목을 몇 단계든 둘 수 있다(예: 정서 변화 → 외로움 표현).
 * 지우지 않고 {@code active=false}로 내린다 — 이전 일지는 작성 당시 문구로 남는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "journal_option")
public class JournalOption {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_code", nullable = false, foreignKey = @ForeignKey(name = "fk_jo_group"))
    private JournalOptionGroup group;

    /** 하위 항목이면 부모 항목. 부모가 선택됐을 때만 고를 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code", foreignKey = @ForeignKey(name = "fk_jo_parent"))
    private JournalOption parent;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /** 고르면 같은 섹션의 다른 항목을 고를 수 없다(예: 특이사항 없음). */
    @Column(name = "exclusive_choice", nullable = false)
    private boolean exclusive;

    /** 고르면 직접 입력이 필요하다(예: 기타). */
    @Column(name = "text_input", nullable = false)
    private boolean textInput;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
