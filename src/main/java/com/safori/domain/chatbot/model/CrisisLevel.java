package com.safori.domain.chatbot.model;

/** 사용자 발화의 자·타해 위험 수준. */
public enum CrisisLevel {
    NONE,
    DISTRESS,
    PASSIVE_IDEATION,
    ACTIVE_INTENT,
    IMMINENT,
    HARM_TO_OTHERS,
    UNKNOWN;

    /** 현재 상담을 중단하고 위기 지원 흐름으로 전환해야 하는 수준인지. */
    public boolean requiresCrisisFlow() {
        // 현재 API가 일반 상담/위기 안내의 이진 흐름만 지원하므로 수동적 사고도 안전 안내로 보낸다.
        // 추후 "안전 여부 확인" 중간 흐름이 생기면 PASSIVE_IDEATION만 그쪽으로 분리할 수 있다.
        return this == PASSIVE_IDEATION || this == ACTIVE_INTENT
                || this == IMMINENT || this == HARM_TO_OTHERS;
    }
}
