package com.safori.infra.ai.gemini.prompts;

import com.safori.domain.chatbot.model.HistoryTurn;

import java.util.List;

public final class VoiceReframingPrompt {

    private VoiceReframingPrompt() {}

    /** 프롬프트에 포함할 최대 히스토리 턴 수. 초과분은 앞에서 잘린다. */
    private static final int HISTORY_LIMIT = 5;

    public static String build(
            String userInput,
            List<HistoryTurn> history,
            int turnCount,
            String address,
            String emotionDesc,
            String emotionHint,
            int maxUserTurns,
            boolean finalTurn
    ) {
        return """
                당신은 따뜻하고 통찰력 있는 전문 심리상담사 '도란이'입니다.
                현재 내담자 **'%s'**와 **음성**으로 대화를 나누고 있으며, 이 세션의 **%d번째 대화**가 진행 중입니다. (총 %d회로 끝나는 상담입니다)
                **[호칭 — 반드시 준수]** 사용자를 '%s'라고 부르세요. '~님'이 아니라 이 호칭(할아버지/할머니)을 그대로 쓰세요.

                [음성 감정 분석 정보]
                %s

                [현재 내담자의 말 (STT)]
                "%s"

                **⭐⭐[핵심 지시사항: 감정의 교차 검증]⭐⭐**
                위 [음성 감정 분석 정보]와 [내담자의 말]을 비교하여 가장 타당한 감정을 도출하세요.
                - 말이 평범한데(Neutral) 음성이 슬픔/불안이면 → 음성 신뢰 (감정 숨김 가능성).
                - 말이 명확히 부정인데 음성이 긍정/중립이면 → 텍스트 신뢰 (음성 모델 오류 가능성).
                - 'Neutral'은 텍스트와 음성 모두 사무적일 때만 선택.
                - 자살/자해/범죄 암시가 보이면 음성 결과 무관하게 위기 개입.
                - **말(STT)이 무의미한 조각이거나, 음성·텍스트 어느 쪽도 신뢰할 근거가 약하면 → 억지 교차검증하지 말고 아래 [불확실성 안전 지침]을 따르세요.**

                %s

                [이전 대화 맥락]
                %s
                %s
                %s

                **[일반 상담 지시사항]**
                1. 반영적 경청: 교차 검증 결과 감정 기반 공감.
                2. 인지 오류 탐지 및 분석.
                3. 일상어 질문: 아래 [질문 방식]을 반드시 따라, 어르신 말을 이어받는 부드러운 일상 질문 한 문장.
                %s
                %s

                **⭐⭐[논리적 일관성 검증]⭐⭐**
                1. '위기 상황' → top_emotion='anxiety'.
                2. 인지 왜곡 감지('없음', '긍정 정서 강화' 제외) → top_emotion≠neutral.
                3. neutral은 '없음' 또는 '긍정 정서 강화'일 때만.
                4. 감정을 확신할 근거가 부족 → detected_distortion='없음', top_emotion='neutral', empathy=범용 공감(불확실성 안전 지침).

                %s
                """.formatted(
                        address,
                        turnCount,
                        maxUserTurns,
                        address,
                        emotionDesc == null || emotionDesc.isBlank() ? "(감정 분석 정보 없음)" : emotionDesc,
                        userInput,
                        EmotionStrategies.UNCERTAINTY_GUIDE,
                        formatHistory(history),
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE,
                        EmotionStrategies.QUESTION_STYLE,
                        EmotionStrategies.LENGTH_GUIDE,
                        ClosingGuide.block(turnCount, maxUserTurns, finalTurn)
                );
    }

    private static String formatHistory(List<HistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음. 대화 시작)";
        }
        // 최근 HISTORY_LIMIT 턴만 포함해 토큰 초과 방지
        List<HistoryTurn> recent = history.size() > HISTORY_LIMIT
                ? history.subList(history.size() - HISTORY_LIMIT, history.size())
                : history;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recent.size(); i++) {
            HistoryTurn t = recent.get(i);
            sb.append("Turn ").append(i + 1).append(":\n");
            sb.append(" - 내담자: ").append(t.userInput() == null ? "" : t.userInput()).append("\n");
            sb.append(" - 상담사: ").append(t.botMessage() == null ? "" : t.botMessage()).append("\n");
        }
        return sb.toString();
    }
}
