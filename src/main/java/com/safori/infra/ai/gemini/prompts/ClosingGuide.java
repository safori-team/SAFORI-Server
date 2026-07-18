package com.safori.infra.ai.gemini.prompts;

/**
 * 대화 턴 제한에 따른 프롬프트 지시. 텍스트/음성 상담 프롬프트가 공유한다.
 *
 * <p>종료 자체는 서버가 강제하므로(ConversationTurnPolicy) 이 지시는 멘트의 톤을 맞추는 용도다.
 * LLM이 이 지시를 무시해도 대화는 여기서 끝난다.
 */
public final class ClosingGuide {

    private ClosingGuide() {}

    /**
     * @param currentTurn  이번이 몇 번째 사용자 발화인지 (1-based)
     * @param maxUserTurns 세션당 허용 발화 횟수
     */
    public static String block(int currentTurn, int maxUserTurns, boolean finalTurn) {
        if (finalTurn) {
            return """

                    **⭐⭐[최우선 지시사항 - 상담 마무리]⭐⭐**
                    이번이 이 상담의 **마지막 대화**입니다. 내담자는 더 이상 답을 보낼 수 없습니다.
                    반드시 아래에 맞춰 대화를 닫아주세요.
                    - `socratic_question`: **질문을 하지 마세요.** 내담자가 답할 수 없는데 질문을 남기면
                      대화가 끊긴 것처럼 느껴집니다. 대신 오늘 나눈 이야기를 한 문장으로 짚어주고,
                      스스로 돌아볼 여지를 남기는 마무리 말을 넣으세요.
                    - `alternative_thought`: 오늘 대화에서 찾은 관점을 정리하고, 일상으로 돌아갈 때
                      지니고 갈 수 있는 따뜻한 응원의 말로 맺으세요.
                    - `empathy`: 여기까지 이야기해준 것에 대한 감사와 격려를 담으세요.
                    - 다음에 또 이야기하자는 여지를 남기되, 구체적인 다음 약속은 하지 마세요.
                    """.formatted();
        }
        int remaining = maxUserTurns - currentTurn;
        return """

                **[대화 분량 안내]**
                이 상담은 내담자 발화 %d회로 끝나며, 지금은 %d번째입니다. 이번 답변 이후
                내담자가 답할 수 있는 기회는 %d번 남았습니다.
                - 한정된 횟수 안에서 핵심에 닿아야 하니, 곁가지 질문으로 턴을 소모하지 마세요.
                - 남은 횟수가 적을수록 탐색보다 정리와 대안 제시에 무게를 두세요.
                - 종료를 먼저 권유하지는 마세요. 마무리 시점은 시스템이 알려줍니다.
                """.formatted(maxUserTurns, currentTurn, remaining);
    }
}
