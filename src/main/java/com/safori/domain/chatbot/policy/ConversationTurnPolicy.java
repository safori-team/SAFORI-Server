package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CBT 상담 세션의 대화 길이 제한.
 *
 * <p>턴은 <b>사용자 발화 횟수</b>로 센다. 마음일기 세션은 도란이의 첫 질문(MIND_DIARY 메시지,
 * 사용자 발화 없음)으로 시작하므로 다음과 같이 흐른다:
 *
 * <pre>
 *   도란이 질문1                      (MIND_DIARY 메시지)
 *   사용자 응답1 + 도란이 질문2        (1턴)
 *   사용자 응답2 + 도란이 질문3        (2턴)
 *   사용자 응답3 + 도란이 질문4        (3턴)
 *   사용자 응답4 + 도란이 마무리 멘트   (4턴 = maxUserTurns → 종료)
 *   그 이후 발화                       → CHAT_SESSION_CLOSED
 * </pre>
 *
 * <p>제한을 프롬프트 지시에만 맡기지 않고 여기서 강제한다 — LLM이 종료 권유를 무시하면
 * 대화가 무한정 길어지기 때문이다. 프롬프트에는 "마지막 턴이니 마무리하라"는 지시를 함께
 * 넣어 멘트의 톤을 맞춘다.
 */
@Component
@RequiredArgsConstructor
public class ConversationTurnPolicy {

    private final ConversationLimitProperties props;
    private final ChatMessageAdaptor chatMessageAdaptor;

    /** 지금까지 사용자가 발화한 횟수. */
    public long userTurns(String sessionId) {
        return chatMessageAdaptor.countUserTurnsBySessionId(sessionId);
    }

    /** 이미 마무리된 세션인지. */
    public boolean isClosed(String sessionId) {
        return userTurns(sessionId) >= props.getMaxUserTurns();
    }

    /**
     * 발화를 받을 수 있는 상태인지 검증한다. LLM 호출 전에 부르는 것을 전제로 한다 —
     * 종료된 세션에 토큰을 쓰지 않기 위해서다.
     *
     * @return 이번 발화의 순번 (1-based)
     * @throws com.safori.common.exception.GeneralException 이미 4턴을 모두 쓴 세션이면
     */
    public long verifyCanSendAndGetTurn(String sessionId) {
        long used = userTurns(sessionId);
        if (used >= props.getMaxUserTurns()) {
            throw ChatbotHandler.SESSION_CLOSED;
        }
        return used + 1;
    }

    /** 이번 발화가 마지막 턴이라 도란이가 마무리 멘트를 해야 하는지. */
    public boolean isFinalTurn(long currentTurn) {
        return currentTurn >= props.getMaxUserTurns();
    }

    public int maxUserTurns() {
        return props.getMaxUserTurns();
    }
}
