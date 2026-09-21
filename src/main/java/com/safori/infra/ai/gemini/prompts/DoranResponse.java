package com.safori.infra.ai.gemini.prompts;

import com.safori.domain.chatbot.model.ChatbotReply;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DoranResponse(
        String empathy,
        @JsonProperty("detected_distortion") String detectedDistortion,
        String analysis,
        @JsonProperty("socratic_question") String socraticQuestion,
        @JsonProperty("alternative_thought") String alternativeThought,
        @JsonProperty("top_emotion") String topEmotion
) {
    /**
     * 인프라 응답을 도메인 모델로 변환한다.
     * AI가 일부 필드를 누락해 null로 파싱된 경우 {@link ChatbotReply#fallback()} 기본값으로 채운다.
     */
    public ChatbotReply toReply() {
        ChatbotReply fb = ChatbotReply.fallback();
        return new ChatbotReply(
                Objects.requireNonNullElse(empathy,            fb.empathy()),
                Objects.requireNonNullElse(detectedDistortion, fb.detectedDistortion()),
                Objects.requireNonNullElse(analysis,           fb.analysis()),
                Objects.requireNonNullElse(socraticQuestion,   fb.socraticQuestion()),
                Objects.requireNonNullElse(alternativeThought, fb.alternativeThought()),
                Objects.requireNonNullElse(topEmotion,         fb.topEmotion())
        );
    }

    public static DoranResponse fallback() {
        return new DoranResponse(
                "죄송해요, 잠시 생각이 꼬였나 봐요.",
                "없음",
                "내용을 불러오지 못했습니다.",
                "오늘 하루는 어떠셨나요?",
                "항상 응원합니다.",
                "neutral"
        );
    }
}
