package com.safori.domain.chatbot.entity;

/**
 * CBT 상담을 즉시 중단시킨 가드레일 신호. {@code chat_session.crisis_trigger}에 그대로 남는다.
 *
 * <p>신호는 서로를 대체하지 않고 겹겹이 쌓인다 — 앞의 것이 놓쳐도 뒤의 것이 잡도록:
 * <ol>
 *   <li>{@link #HIGH_RISK_KEYWORD} — LLM 호출 <b>전에</b> 서버가 직접 (토큰 0)</li>
 *   <li>{@link #AI_CRISIS_CLASSIFIER} — 전용 문맥 분류기가 현재의 적극적 자·타해 위험을 판정</li>
 *   <li>{@link #CRISIS_DISTORTION} — 상담 모델이 응답 안에서 스스로 위기로 판정한 레거시 안전망</li>
 * </ol>
 */
public enum CrisisTrigger {

    /**
     * 사용자 발화에 고위험 표현이 직접 담겨 서버 사전 스크리닝에 걸렸다.
     * LLM을 아예 호출하지 않으므로 위험 발화가 모델에 도달하지 않는다.
     */
    HIGH_RISK_KEYWORD,

    /** 전용 문맥 분류기가 현재의 적극적 자·타해 위험을 판정했다. */
    AI_CRISIS_CLASSIFIER,

    /**
     * 과거 버전에서 Gemini 안전 필터 차단을 위기로 기록한 값. 기존 DB 값 호환을 위해 유지한다.
     * 신규 판정에서는 사용하지 않는다.
     *
     * @see <a href="https://ai.google.dev/gemini-api/docs/safety-settings">Gemini 안전 설정</a>
     */
    SAFETY_BLOCKED,

    /**
     * 응답은 생성됐지만 모델이 {@code detected_distortion="위기 상황"}으로 판정했다.
     * 프롬프트의 위기 개입 지시가 발동한 경우다.
     */
    CRISIS_DISTORTION
}
