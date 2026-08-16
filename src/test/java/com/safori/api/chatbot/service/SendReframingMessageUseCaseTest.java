package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.ReframingRequest;
import com.safori.api.chatbot.dto.ReframingResponse;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.policy.CrisisGuardrailPolicy;
import com.safori.domain.chatbot.policy.CrisisVerdict;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.GeneratedReply;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
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
    @Mock CrisisGuardrailPolicy crisisPolicy;
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
        given(geminiChatbotClient.generate(anyString())).willReturn(GeneratedReply.ok(
                new ChatbotReply("공감", "없음", "분석", "질문", "대안", "sad")));
        given(chatbotDomainService.beginMessage(
                anyString(), anyString(), any(MessageOrigin.class), any()))
                .willReturn(101L);
        // 기본은 가드레일에 걸리지 않는 정상 대화
        given(crisisPolicy.screen(anyString())).willReturn(CrisisVerdict.none());
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

    @Test
    @DisplayName("이미 위기로 닫힌 세션이면 턴이 남아 있어도 거부된다")
    void rejectsCrisisClosedSession() {
        givenSession();
        org.mockito.BDDMockito.willThrow(ChatbotHandler.SESSION_CRISIS_CLOSED)
                .given(crisisPolicy).verifyNotCrisisClosed(session);

        assertThatThrownBy(() -> useCase.execute(USERNAME, request))
                .isSameAs(ChatbotHandler.SESSION_CRISIS_CLOSED);

        verifyNoInteractions(geminiChatbotClient);
    }

    @Test
    @DisplayName("사전 스크리닝에 걸리면 LLM을 호출하지 않고 위기 안내로 세션을 닫는다")
    void preScreenCrisisSkipsLlm() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(1L);
        given(crisisPolicy.screen(anyString())).willReturn(
                CrisisVerdict.of(CrisisTrigger.HIGH_RISK_KEYWORD, "keyword=자살"));
        given(chatbotDomainService.appendMessage(
                anyString(), anyString(), any(ChatbotReply.class), any(MessageOrigin.class), any()))
                .willReturn(202L);

        ReframingResponse response = useCase.execute(USERNAME, request);

        // 위험 발화가 모델에 도달하지 않는다 — 토큰도 쓰지 않는다
        verifyNoInteractions(geminiChatbotClient);
        assertThat(response.messageId()).isEqualTo(202L);
        assertThat(response.crisisDetected()).isTrue();
        assertThat(response.sessionClosed()).isTrue();
        assertThat(response.crisisTrigger()).isEqualTo("HIGH_RISK_KEYWORD");
        assertThat(response.detectedDistortion()).isEqualTo("위기 상황");
        assertThat(response.analysis()).contains("109");
        verify(chatbotDomainService).closeSessionByCrisis(SESSION_ID, CrisisTrigger.HIGH_RISK_KEYWORD);
    }

    @Test
    @DisplayName("안전 필터 차단만으로 사용자를 위기로 판정하거나 세션을 닫지 않는다")
    void safetyBlockDoesNotCloseSession() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(2L);
        given(turnPolicy.isFinalTurn(2L)).willReturn(false);
        given(geminiChatbotClient.generate(anyString()))
                .willReturn(GeneratedReply.safetyBlocked("promptBlockReason=SAFETY"));

        ReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.crisisDetected()).isFalse();
        assertThat(response.sessionClosed()).isFalse();
        assertThat(response.crisisTrigger()).isNull();
        verify(chatbotDomainService, org.mockito.Mockito.never())
                .closeSessionByCrisis(anyString(), any());

        // API 호출 오류는 아니므로 기존 정책대로 FAILED로 기록하지 않는다.
        ArgumentCaptor<Boolean> failed = ArgumentCaptor.forClass(Boolean.class);
        verify(chatbotDomainService).settleMessage(
                org.mockito.ArgumentMatchers.eq(101L), any(ChatbotReply.class), failed.capture());
        assertThat(failed.getValue()).isFalse();
    }

    @Test
    @DisplayName("전용 사전 분류를 통과한 발화는 상담 모델 응답이 위기 라벨이어도 세션을 닫지 않는다")
    void generatedCrisisLabelCannotOverrideClassifier() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(1L);
        given(turnPolicy.isFinalTurn(1L)).willReturn(false);
        given(geminiChatbotClient.generate(anyString())).willReturn(GeneratedReply.ok(
                new ChatbotReply("공감", "위기 상황", "분석", "질문", "대안", "anxiety")));

        ReframingResponse response = useCase.execute(USERNAME,
                new ReframingRequest(SESSION_ID, "죽고싶었는데 지금은 아냐", "sad"));

        assertThat(response.crisisDetected()).isFalse();
        assertThat(response.sessionClosed()).isFalse();
        verify(chatbotDomainService, org.mockito.Mockito.never())
                .closeSessionByCrisis(anyString(), any());
    }

    @Test
    @DisplayName("정상 대화는 crisisDetected=false로 나가고 세션을 닫지 않는다")
    void normalTurnReportsNoCrisis() {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(2L);
        given(turnPolicy.isFinalTurn(2L)).willReturn(false);

        ReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.crisisDetected()).isFalse();
        assertThat(response.crisisTrigger()).isNull();
        verify(chatbotDomainService, org.mockito.Mockito.never())
                .closeSessionByCrisis(anyString(), any());
    }
}
