package com.safori.domain.chatbot.model;

/**
 * LLM 생성 결과. 응답 본문과 "실제로 생성에 성공했는지"를 함께 나른다.
 *
 * <p>실패 시에도 사용자에게는 폴백 멘트를 그대로 보여주므로 응답 본문만으로는 성공·실패를
 * 구분할 수 없다. {@code failed} 플래그가 있어야 메시지를
 * {@link com.safori.domain.chatbot.entity.ChatReplyStatus#FAILED}로 기록할 수 있다.
 */
public record GeneratedReply(ChatbotReply reply, boolean failed) {

    public static GeneratedReply ok(ChatbotReply reply) {
        return new GeneratedReply(reply, false);
    }

    /** 생성 실패 — 폴백 응답을 담아 반환한다. */
    public static GeneratedReply fallback() {
        return new GeneratedReply(ChatbotReply.fallback(), true);
    }
}
