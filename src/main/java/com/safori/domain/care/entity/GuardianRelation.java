package com.safori.domain.care.entity;

/**
 * 보호자와 어르신의 관계. 기타면 직접 입력한 값({@code relation_text})을 함께 둔다.
 */
public enum GuardianRelation {
    CHILD("자녀"),
    SPOUSE("배우자"),
    SIBLING("형제/자매"),
    OTHER("기타");

    private final String label;

    GuardianRelation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 화면 표시용 관계. 기타면 입력값, 관계가 없으면 null. */
    public static String labelOf(GuardianRelation relation, String relationText) {
        if (relation == null) {
            return null;
        }
        return relation == OTHER && relationText != null ? relationText : relation.label;
    }
}
