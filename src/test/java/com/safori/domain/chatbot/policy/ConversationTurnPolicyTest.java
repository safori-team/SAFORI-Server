package com.safori.domain.chatbot.policy;

import com.safori.common.exception.GeneralException;
import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.common.exception.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConversationTurnPolicyTest {

    private static final String SESSION_ID = "session-1";

    private FakeMessageAdaptor adaptor;
    private ConversationLimitProperties props;
    private ConversationTurnPolicy policy;

    @BeforeEach
    void setUp() {
        adaptor = new FakeMessageAdaptor();
        props = new ConversationLimitProperties();
        policy = new ConversationTurnPolicy(props, adaptor);
    }

    @Test
    @DisplayName("마음일기 세션: 도란이 질문 → 사용자 응답이 4번 오가고 4번째 응답에 마무리 멘트가 나간다")
    void mindDiarySessionEndsAfterFourUserTurns() {
        // 도란이 첫 질문(MIND_DIARY)은 사용자 발화가 아니므로 턴에 포함되지 않는다
        adaptor.userTurns = 0;
        assertThat(policy.verifyCanSendAndGetTurn(SESSION_ID)).isEqualTo(1);
        assertThat(policy.isFinalTurn(1)).isFalse();

        adaptor.userTurns = 1;
        assertThat(policy.verifyCanSendAndGetTurn(SESSION_ID)).isEqualTo(2);
        assertThat(policy.isFinalTurn(2)).isFalse();

        adaptor.userTurns = 2;
        assertThat(policy.verifyCanSendAndGetTurn(SESSION_ID)).isEqualTo(3);
        assertThat(policy.isFinalTurn(3)).isFalse();

        adaptor.userTurns = 3;
        assertThat(policy.verifyCanSendAndGetTurn(SESSION_ID)).isEqualTo(4);
        assertThat(policy.isFinalTurn(4)).isTrue();  // 마무리 멘트
    }

    @Test
    @DisplayName("4턴을 다 쓰면 세션이 닫히고 추가 발화는 CHAT_SESSION_CLOSED로 거부된다")
    void rejectsSendAfterLimit() {
        adaptor.userTurns = 4;

        assertThat(policy.isClosed(SESSION_ID)).isTrue();
        assertThatThrownBy(() -> policy.verifyCanSendAndGetTurn(SESSION_ID))
                .isInstanceOf(GeneralException.class)
                .isSameAs(com.safori.domain.chatbot.exception.ChatbotHandler.SESSION_CLOSED);
    }

    @Test
    @DisplayName("마지막 턴을 쓰기 전까지는 세션이 열려 있다")
    void notClosedBeforeLimit() {
        adaptor.userTurns = 3;

        assertThat(policy.isClosed(SESSION_ID)).isFalse();
        assertThatCode(() -> policy.verifyCanSendAndGetTurn(SESSION_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("max-user-turns를 6으로 올리면 6턴까지 대화할 수 있다")
    void maxTurnsIsConfigurable() {
        props.setMaxUserTurns(6);
        adaptor.userTurns = 4;

        assertThat(policy.isClosed(SESSION_ID)).isFalse();
        assertThat(policy.verifyCanSendAndGetTurn(SESSION_ID)).isEqualTo(5);
        assertThat(policy.isFinalTurn(5)).isFalse();
        assertThat(policy.isFinalTurn(6)).isTrue();
    }

    /** userTurns만 흉내내는 스텁. */
    private static class FakeMessageAdaptor implements ChatMessageAdaptor {

        long userTurns;

        @Override
        public long countUserTurnsBySessionId(String sessionId) {
            return userTurns;
        }

        // -- 이 테스트에서 쓰지 않는 메서드 --------------------------------
        @Override public ChatMessage queryById(Long messageId) { throw new UnsupportedOperationException(); }
        @Override public ChatMessage queryByIdWithSessionAndUser(Long messageId) { throw new UnsupportedOperationException(); }
        @Override public Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<ChatMessage> queryRecentBySessionId(String sessionId, int limit) { throw new UnsupportedOperationException(); }
        @Override public long countBySessionId(String sessionId) { throw new UnsupportedOperationException(); }
        @Override public Map<String, Long> countUserTurnsBySessionIds(List<String> sessionIds) { throw new UnsupportedOperationException(); }
        @Override public boolean existsProcessingBySessionId(String sessionId) { throw new UnsupportedOperationException(); }
        @Override public List<ChatMessage> queryStaleProcessing(java.time.LocalDateTime threshold) { throw new UnsupportedOperationException(); }
        @Override public Optional<ChatMessage> queryLatestBySessionId(String sessionId) { throw new UnsupportedOperationException(); }
        @Override public Map<String, ChatMessage> queryLatestBySessionIds(List<String> sessionIds) { throw new UnsupportedOperationException(); }
        @Override public Optional<ChatMessage> findMindDiaryMessageByVoiceId(Long voiceId) { throw new UnsupportedOperationException(); }
        @Override public Map<Long, ChatMessage> findMindDiaryMessagesByVoiceIds(List<Long> voiceIds) { throw new UnsupportedOperationException(); }
        @Override public ChatMessage save(ChatMessage message) { throw new UnsupportedOperationException(); }
    }
}
