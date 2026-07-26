package com.safori.domain.chatbot.model;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * 불확실 상황(STT 완전 실패 등)에서 LLM을 거치지 않고 반환하는 안전 응답.
     * 감정·인지오류를 단정하지 않고, 더 들려달라는 범용 일상 공감만 담아 오답을 원천 차단한다.
     * 로봇처럼 들리지 않도록 여러 변형 중 하나를 무작위로 고른다.
     *
     * @param address 사용자 호칭(예: "홍길동 할아버지"). null/blank면 호칭 없이 자연스럽게 처리.
     */
    public static ChatbotReply safeGeneric(String address) {
        String name = (address == null || address.isBlank()) ? "" : address + ", ";
        List<String> empathies = List.of(
                name + "오늘 그런 일이 있으셨군요. 마음이 어떠셨을지 도란이에게 조금 더 들려주실 수 있나요?",
                name + "말씀 잘 들었어요. 오늘 하루 마음은 어떠셨는지 조금만 더 이야기해 주실래요?",
                name + "지금 떠오르는 마음을 편하게 말씀해 주셔도 괜찮아요. 어떤 기분이신지 궁금해요."
        );
        List<String> questions = List.of(
                "오늘 마음에 가장 남는 순간이 있었다면, 어떤 순간이었을까요?",
                "지금 마음이 어떤 색깔에 가까운지 편하게 말씀해 주실래요?",
                "무슨 이야기든 좋아요. 어떤 마음을 더 나누고 싶으신가요?"
        );
        int i = ThreadLocalRandom.current().nextInt(empathies.size());
        return new ChatbotReply(
                empathies.get(i),
                "없음",
                "천천히, 편하신 만큼만 이야기해 주셔도 괜찮아요.",
                questions.get(i),
                "도란이는 언제든 곁에서 이야기를 기다리고 있을게요.",
                "neutral"
        );
    }
}
