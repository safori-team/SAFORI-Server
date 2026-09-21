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
     * 위기 가드레일이 발동했을 때 LLM을 거치지 않고 반환하는 응답.
     *
     * <p>상담 기법(공감 → 인지 왜곡 분석 → 소크라테스식 질문)을 전부 버리고 안전 안내만 남긴다.
     * 자·타해 의도가 드러난 사람에게 필요한 것은 상담이 아니라 사람의 개입이기 때문이다.
     * 응답 <b>형상</b>은 일반 응답과 같아서 프론트가 렌더링을 분기할 필요는 없다.
     *
     * <p>{@code detected_distortion="위기 상황"} / {@code top_emotion="anxiety"}는 프롬프트의
     * 논리적 일관성 규칙과 같은 값을 쓴다 — 이 응답이 히스토리·세션 목록에서도 위기 턴으로
     * 동일하게 보이도록.
     *
     * @param address 사용자 호칭(예: "홍길동 할아버지"). null/blank면 호칭 없이 처리.
     */
    public static ChatbotReply crisis(String address) {
        String name = (address == null || address.isBlank()) ? "" : address + ", ";
        return new ChatbotReply(
                name + "마음이 많이 무거우셨겠어요. 그 이야기를 들으니 도란이도 마음이 쓰이네요.",
                "위기 상황",
                "지금은 도란이와 이야기를 이어가는 것보다, 사람이 곁에서 함께해 주는 것이 더 큰 힘이 될 것 같아요. "
                        + "마음이 힘드실 때 언제든 이야기 나눌 수 있는 109 상담전화는 24시간 무료로 연결되고, "
                        + "많이 급하실 때는 119로 연락하셔도 괜찮아요.",
                "지금 곁에 계신 분이나 가족에게 힘든 마음을 알려주세요. 혼자 계시지 않는 게 중요해요.",
                "이렇게 마음을 꺼내 주신 것만으로도 참 잘하신 일이에요. 도란이는 언제나 곁에 있을게요.",
                "anxiety"
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
