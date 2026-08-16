package com.safori.domain.chatbot.model;

/**
 * LLM 생성 결과. 응답 본문과 "실제로 생성에 성공했는지"를 함께 나른다.
 *
 * <p>실패 시에도 사용자에게는 폴백 멘트를 그대로 보여주므로 응답 본문만으로는 성공·실패를
 * 구분할 수 없다. {@code failed} 플래그가 있어야 메시지를
 * {@link com.safori.domain.chatbot.entity.ChatReplyStatus#FAILED}로 기록할 수 있다.
 *
 * @param reply         응답 본문 (실패·차단 시 폴백 멘트)
 * @param failed        생성에 실패해 메시지를 FAILED로 기록해야 하는지
 * @param safetyBlocked Gemini 안전 필터가 프롬프트 또는 응답을 차단했는지.
 *                      {@code failed}와 별개다 — 차단은 호출 오류가 아니라 가드레일 신호이며,
 *                      호출자는 이 플래그를 보고 상담을 중단시킨다
 *                      ({@link com.safori.domain.chatbot.policy.CrisisGuardrailPolicy}).
 * @param safetyDetail  차단 근거 (blockReason·finishReason·카테고리). 로그·운영용, 사용자 비노출.
 */
public record GeneratedReply(ChatbotReply reply, boolean failed,
                             boolean safetyBlocked, String safetyDetail) {

    public static GeneratedReply ok(ChatbotReply reply) {
        return new GeneratedReply(reply, false, false, null);
    }

    /** 생성 실패 — 폴백 응답을 담아 반환한다. */
    public static GeneratedReply fallback() {
        return new GeneratedReply(ChatbotReply.fallback(), true, false, null);
    }

    /**
     * 안전 필터 차단 — 응답 본문이 없다.
     *
     * <p>{@code failed=false}인 이유: 호출은 정상적으로 끝났고 모델이 의도대로 막은 것이라
     * 메시지를 FAILED(=재시도 대상)로 기록하면 오해를 부른다. 본문에는 폴백을 담아 두지만
     * 호출자가 위기 안내 응답으로 갈아끼우므로 사용자에게 노출되지는 않는다.
     */
    public static GeneratedReply safetyBlocked(String detail) {
        return new GeneratedReply(ChatbotReply.fallback(), false, true, detail);
    }
}
