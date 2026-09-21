package com.safori.api.chatbot.service;

import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.policy.CrisisGuardrailPolicy;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExtendChatSessionUseCaseTest {

    private static final String SESSION_ID = "session-1";
    private static final String USERNAME = "tester";

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock ConversationTurnPolicy turnPolicy;
    @Mock CrisisGuardrailPolicy crisisPolicy;
    @Mock User user;
    @Mock ChatSession session;

    @InjectMocks ExtendChatSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        given(userAdaptor.queryUserByUsername(USERNAME)).willReturn(user);
        given(chatSessionAdaptor.queryById(SESSION_ID)).willReturn(session);
        given(session.getId()).willReturn(SESSION_ID);
    }

    @Test
    @DisplayName("턴을 다 쓴 세션은 연장된다")
    void extendsClosedSession() {
        given(turnPolicy.isClosed(session)).willReturn(true);

        useCase.execute(USERNAME, SESSION_ID);

        verify(chatbotDomainService).extendSession(SESSION_ID);
    }

    @Test
    @DisplayName("턴이 남은 세션은 4213으로 거부된다")
    void rejectsOpenSession() {
        given(turnPolicy.isClosed(session)).willReturn(false);

        assertThatThrownBy(() -> useCase.execute(USERNAME, SESSION_ID))
                .isSameAs(ChatbotHandler.SESSION_NOT_EXTENDABLE);
        verify(chatbotDomainService, never()).extendSession(anyString());
    }

    @Test
    @DisplayName("위기로 닫힌 세션은 연장할 수 없다")
    void rejectsCrisisClosedSession() {
        willThrow(ChatbotHandler.SESSION_CRISIS_CLOSED).given(crisisPolicy).verifyNotCrisisClosed(session);

        assertThatThrownBy(() -> useCase.execute(USERNAME, SESSION_ID))
                .isSameAs(ChatbotHandler.SESSION_CRISIS_CLOSED);
        verify(chatbotDomainService, never()).extendSession(anyString());
    }

    @Test
    @DisplayName("이미 연장된 세션에 다시 호출해도 성공한다 (멱등)")
    void idempotentWhenAlreadyExtended() {
        given(session.isExtended()).willReturn(true);

        useCase.execute(USERNAME, SESSION_ID);

        verify(chatbotDomainService, never()).extendSession(anyString());
    }
}
