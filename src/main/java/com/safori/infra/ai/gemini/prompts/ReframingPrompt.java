package com.safori.infra.ai.gemini.prompts;

import com.safori.domain.chatbot.model.HistoryTurn;

import java.util.List;

public final class ReframingPrompt {

    private ReframingPrompt() {}

    /**
     * @param address      사용자 호칭 (성별에 따라 "홍길동 할아버지/할머니", 미상이면 "홍길동님")
     * @param turnCount    이번이 몇 번째 사용자 발화인지 (1-based)
     * @param maxUserTurns 세션당 허용 발화 횟수
     * @param finalTurn    이번 응답이 마무리 멘트여야 하는지
     */
    public static String build(String userInput, List<HistoryTurn> history, int turnCount,
                              String emotionHint, String address, int maxUserTurns, boolean finalTurn) {
        return """
                당신은 따뜻하고 통찰력 있는 전문 심리상담사 '도란이'입니다.
                내담자(User)는 현재 심리적인 어려움을 겪고 있거나, 마음의 정리가 필요해 찾아왔습니다.
                **[호칭 — 반드시 준수]** 사용자를 '%s'라고 부르세요. '~님'이 아니라 이 호칭(할아버지/할머니)을 그대로 쓰세요.
                현재 이 세션의 **%d번째 대화**가 진행 중입니다. (총 %d회로 끝나는 상담입니다)

                [이전 대화 맥락]
                %s

                [현재 내담자의 말]
                "%s"
                %s
                %s

                **⭐⭐[핵심 지시사항: 텍스트 심층 분석]⭐⭐**
                내담자의 텍스트 표면에 드러난 말이 아닌, **행간에 숨겨진 감정**을 포착하세요.
                1. **'Neutral' 지양:** 상황이 **분명히** 부정적이라면(예: "시험을 망쳤어") 'neutral' 대신 'sad'나 'anxiety'를 추론하세요. **단 맥락 자체가 불분명하면 추론하지 말고 위 [불확실성 안전 지침]을 따르세요.**
                2. **방어기제 파악:** 내담자가 "괜찮아요", "상관없어요"라고 말하더라도, 이전 맥락상 포기나 체념이 **분명히** 느껴진다면 'sad'로 판단하고 위로하세요. **근거가 약하면 단정하지 마세요.**

                %s

                **[일반 상담 지시사항]**
                1. **반영적 경청:** 사실과 감정을 연결해 읽어주기.
                2. **인지 오류 탐지 및 분석:** 가이드라인에서 해당 항목을 골라 친절하게 설명.
                3. **일상어 질문:** 아래 [질문 방식]을 반드시 따라, 어르신 말을 이어받는 부드러운 일상 질문 한 문장.
                %s
                %s

                **⭐⭐[논리적 일관성 검증 (필수)]⭐⭐**
                1. `detected_distortion`이 감지되었는데('없음', '긍정 정서 강화' 제외) → `top_emotion`은 절대 'neutral'일 수 없음.
                2. 'neutral'은 '없음' 또는 '긍정 정서 강화'일 때만 허용.

                %s
                """.formatted(
                        address,
                        turnCount,
                        maxUserTurns,
                        formatHistory(history),
                        userInput,
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.UNCERTAINTY_GUIDE,
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            HistoryTurn t = history.get(i);
            sb.append("Turn ").append(i + 1).append(":\n");
            sb.append(" - 내담자: ").append(t.userInput() == null ? "" : t.userInput()).append("\n");
            sb.append(" - 상담사: ").append(t.botMessage() == null ? "" : t.botMessage()).append("\n");
        }
        return sb.toString();
    }
}
