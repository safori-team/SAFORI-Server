package com.safori.domain.chatbot.entity;

/**
 * 마음일기 상담 제안의 생애주기.
 *
 * <pre>
 *   OFFERED ──accept──▶ ACCEPTED   (세션 생성)
 *      │
 *      └────decline──▶ DECLINED    (재제안 없음)
 * </pre>
 *
 * 어느 상태든 원장 행이 존재하면 스캔에서 제외된다(같은 일기 재제안 방지).
 * 재평가가 필요하면(재분석으로 조건이 깨진 OFFERED/0턴 ACCEPTED) 원장 행 자체를 삭제한다.
 */
public enum MindDiaryTriggerStatus {
    OFFERED,
    ACCEPTED,
    DECLINED
}
