package com.safori.domain.emotion.entity;

/**
 * 소분류 감정 분석 요청 1건의 처리 상태.
 *
 * <p>{@link #PENDING}을 제외한 나머지는 모두 최종 상태다. Standard SQS는 같은 응답을 두 번
 * 이상 전달할 수 있으므로, 최종 상태 레코드에 도착한 응답은 오류가 아니라 중복으로 보고
 * 아무 것도 하지 않은 채 ACK한다.
 */
public enum EmotionAnalysisStatus {

    /** 요청 큐로 전송 완료, 응답 대기 중. */
    PENDING,

    /** 응답 수신 성공 — 소분류 라벨을 반영했다. */
    COMPLETED,

    /** 분석 Lambda가 4xx를 반환 — 오류 본문을 보관하고 Gemini 소분류를 유지한다. */
    FAILED,

    /** 큐 전송 자체가 실패 — 소분류 판정 없이 Gemini 결과로 마감했다. */
    SEND_FAILED,

    /**
     * 응답이 제한 시간 안에 오지 않아 스윕으로 마감했다.
     * 분석 Lambda 5xx·타임아웃처럼 응답 자체가 오지 않는 경로가 여기 걸린다.
     */
    TIMEOUT;

    public boolean isFinal() {
        return this != PENDING;
    }
}
