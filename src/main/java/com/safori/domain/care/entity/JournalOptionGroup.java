package com.safori.domain.care.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일지 폼의 섹션(확인 방식, 확인 결과, 대상자 상태 …). 항목은 {@link JournalOption}.
 * 폼은 이 데이터로 그리므로 섹션·항목이 바뀌어도 테이블과 앱은 그대로다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "journal_option_group")
public class JournalOptionGroup {

    public enum Selection { SINGLE, MULTI }

    @Id
    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection", nullable = false, columnDefinition = "VARCHAR(8)")
    private Selection selection;

    /** 하나 이상 골라야 하는지. */
    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
