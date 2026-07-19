package com.safori.domain.chatbot.model;

/** 도란이 AI 응답의 도메인 표현. 인프라 타입(DoranResponse)에 독립적이다. */
public record ChatbotReply(
        String empathy,
        String detectedDistortion,
        String analysis,
        String socraticQuestion,
        String alternativeThought,
        String topEmotion
) {
    public static ChatbotReply fallback() {
        return new ChatbotReply(
                "죄송해요, 잠시 생각이 꼬였나 봐요.",
                "없음",
                "내용을 불러오지 못했습니다.",
                "오늘 하루는 어떠셨나요?",
                "항상 응원합니다.",
                "neutral"
        );
    }
}
