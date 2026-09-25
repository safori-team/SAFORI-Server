package com.safori.domain.care.model;

/** 일지에서 고른 항목 코드와, 직접 입력 항목(기타)이면 입력값. */
public record JournalSelectionInput(String optionCode, String text) {
}
