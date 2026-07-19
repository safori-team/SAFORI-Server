package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.ReframingRequest;
import com.safori.api.chatbot.dto.ReframingResponse;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.prompts.DoranResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SendReframingMessageUseCaseTest {

    private static final String SESSION_ID = "session-1";
    private static final String USERNAME = "tester";

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock ConversationTurnPolicy turnPolicy;
    @Mock GeminiChatbotClient geminiChatbotClient;
    @Mock User user;
    @Mock ChatSession session;

    @InjectMocks SendReframingMessageUseCase useCase;

    private final ReframingRequest request =
            new ReframingRequest(SESSION_ID, "오늘도 너무 힘들었어요", "sad");

    private void givenSession() {
        given(userAdaptor.queryUserByUsername(USERNAME)).willReturn(user);
        given(chatSessionAdaptor.queryById(SESSION_ID)).willReturn(session);
        given(session.getId()).willReturn(SESSION_ID);
        given(chatbotDomainService.loadRecentHistory(anyString(), anyInt())).willReturn(List.of());
        given(turnPolicy.maxUserTurns()).willReturn(4);
        given(geminiChatbotClient.generate(anyString())).willReturn(new DoranResponse(
                "공감", "없음", "분석", "질문", "대안", "sad"));
        given(chatbotDomainService.appendMessage(
                anyString(), anyString(), any(), any(MessageOrigin.class), any()))
                .willReturn(101L);
    }

    @Test
    @DisplayName("턴이 소진된 세션이면 LLM을 호출하지 않고 즉시 거부한다 — 종료된 세션에 토큰을 쓰지 않는다")
    void rejectsClosedSessionBeforeCallingLlm() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID))
                .willThrow(ChatbotHandler.SESSION_CLOSED);

        assertThatThrownBy(() -> useCase.execute(USERNAME, request))
                .isSameAs(ChatbotHandler.SESSION_CLOSED);

        verifyNoInteractions(geminiChatbotClient);
        verify(chatbotDomainService, org.mockito.Mockito.never())
                .appendMessage(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("마지막 턴이면 마무리 지시가 담긴 프롬프트를 보내고 sessionClosed=true로 응답한다")
    void finalTurnClosesSession() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(4L);
        given(turnPolicy.isFinalTurn(4L)).willReturn(true);

        ReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.sessionClosed()).isTrue();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiChatbotClient).generate(prompt.capture());
        assertThat(prompt.getValue())
                .contains("상담 마무리")
                .contains("질문을 하지 마세요");
    }

    @Test
    @DisplayName("마지막 턴이 아니면 sessionClosed=false이고 마무리 지시도 들어가지 않는다")
    void normalTurnKeepsSessionOpen() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(2L);
        given(turnPolicy.isFinalTurn(2L)).willReturn(false);

        ReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.sessionClosed()).isFalse();

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiChatbotClient).generate(prompt.capture());
        assertThat(prompt.getValue()).doesNotContain("상담 마무리");
        assertThat(prompt.getValue()).contains("2번째 대화");
    }

    @Test
    @DisplayName("소유자가 아니면 턴 검증에 도달하기 전에 막힌다")
    void verifiesOwnershipFirst() {
        givenSession();
        org.mockito.BDDMockito.willThrow(ChatbotHandler.SESSION_NO_PERMISSION)
                .given(chatbotDomainService).verifyOwnership(session, user);

        assertThatThrownBy(() -> useCase.execute(USERNAME, request))
                .isSameAs(ChatbotHandler.SESSION_NO_PERMISSION);

        verifyNoInteractions(geminiChatbotClient);
    }
}
