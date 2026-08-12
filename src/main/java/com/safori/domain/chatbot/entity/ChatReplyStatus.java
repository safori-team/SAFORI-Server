package com.safori.domain.chatbot.entity;

/**
 * 도란이 응답 생성 상태.
 *
 * <pre>
 *   PROCESSING ──LLM 성공──▶ COMPLETED
 *        │
 *        └──LLM 실패 · 5분 초과──▶ FAILED
 * </pre>
 *
 * <p>메시지 행을 LLM 호출 <b>전에</b> PROCESSING으로 먼저 커밋하기 때문에, 응답을 기다리는
 * 동안 사용자가 화면을 벗어나 목록/상세를 조회해도 "처리 중"임을 알 수 있다.
 *
 * <p>대기 큐가 없어 {@code Voice.AnalysisStatus}와 달리 PENDING은 두지 않는다.
 */
public enum ChatReplyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
