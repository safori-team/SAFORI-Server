package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.VoiceReframingRequest;
import com.safori.api.chatbot.dto.VoiceReframingResponse;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.VoiceEmotionDigest;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.policy.CrisisGuardrailPolicy;
import com.safori.domain.chatbot.policy.CrisisVerdict;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.GeneratedReply;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.chatbot.service.ChatbotMessageMapper;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.GeminiEmotionMapper;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
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
class SendVoiceReframingMessageUseCaseTest {

    private static final String SESSION_ID = "session-1";
    private static final String USERNAME = "tester";

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock ConversationTurnPolicy turnPolicy;
    @Mock CrisisGuardrailPolicy crisisPolicy;
    @Mock ChatbotMessageMapper mapper;
    @Mock GeminiChatbotClient geminiChatbotClient;
    @Mock GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    @Mock GeminiEmotionMapper emotionMapper;
    @Mock GeminiAnalysisResult analysisResult;
    @Mock User user;
    @Mock ChatSession session;

    @InjectMocks SendVoiceReframingMessageUseCase useCase;

    private final VoiceReframingRequest request =
            new VoiceReframingRequest(SESSION_ID, "voices/2026/07/a.m4a");

    private void givenSession() throws Exception {
        given(userAdaptor.queryUserByUsername(USERNAME)).willReturn(user);
        given(chatSessionAdaptor.queryById(SESSION_ID)).willReturn(session);
        given(session.getId()).willReturn(SESSION_ID);
        given(user.getName()).willReturn("테스터");
        given(geminiVoiceAnalyzer.transcribeAndAnalyze(anyString())).willReturn(analysisResult);
        given(analysisResult.transcript()).willReturn("오늘도 너무 힘들었어요");
        given(emotionMapper.toVoiceEmotionDigest(any(), anyInt()))
                .willReturn(new VoiceEmotionDigest(EmotionType.SAD, 5000, List.of()));
        given(mapper.summarizeVoiceEmotion(any(EmotionType.class), any(), any())).willReturn("- 주된 감정: sad");
        given(mapper.emotionHint(any(EmotionType.class))).willReturn("sad");
        given(chatbotDomainService.loadRecentHistory(anyString(), anyInt())).willReturn(List.of());
        given(turnPolicy.maxUserTurns()).willReturn(4);
        given(geminiChatbotClient.generate(anyString())).willReturn(GeneratedReply.ok(
                new ChatbotReply("공감", "없음", "분석", "질문", "대안", "sad")));
        given(chatbotDomainService.beginMessage(
                anyString(), anyString(), any(MessageOrigin.class), any()))
                .willReturn(101L);
        // 기본은 가드레일에 걸리지 않는 정상 대화
        given(crisisPolicy.screen(anyString())).willReturn(CrisisVerdict.none());
        given(crisisPolicy.inspect(any())).willReturn(CrisisVerdict.none());
    }

    @Test
    @DisplayName("턴이 소진됐으면 STT(Flash)조차 호출하지 않는다 — 음성 경로는 LLM을 두 번 타므로 비용 영향이 크다")
    void rejectsClosedSessionBeforeStt() throws Exception {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID))
                .willThrow(ChatbotHandler.SESSION_CLOSED);

        assertThatThrownBy(() -> useCase.execute(USERNAME, request))
                .isSameAs(ChatbotHandler.SESSION_CLOSED);

        verifyNoInteractions(geminiVoiceAnalyzer);
        verifyNoInteractions(geminiChatbotClient);
    }

    @Test
    @DisplayName("마지막 턴이면 마무리 지시 프롬프트 + sessionClosed=true")
    void finalTurnClosesSession() throws Exception {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(4L);
        given(turnPolicy.isFinalTurn(4L)).willReturn(true);

        VoiceReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.sessionClosed()).isTrue();
        assertThat(response.content()).isEqualTo("오늘도 너무 힘들었어요");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiChatbotClient).generate(prompt.capture());
        assertThat(prompt.getValue()).contains("상담 마무리");
    }

    @Test
    @DisplayName("마지막 턴이 아니면 sessionClosed=false")
    void normalTurnKeepsSessionOpen() throws Exception {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(1L);
        given(turnPolicy.isFinalTurn(1L)).willReturn(false);

        VoiceReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.sessionClosed()).isFalse();
        assertThat(response.crisisDetected()).isFalse();
        assertThat(response.crisisTrigger()).isNull();
    }

    @Test
    @DisplayName("이미 위기로 닫힌 세션이면 STT(Flash)조차 호출하지 않는다")
    void rejectsCrisisClosedSessionBeforeStt() throws Exception {
        givenSession();
        org.mockito.BDDMockito.willThrow(ChatbotHandler.SESSION_CRISIS_CLOSED)
                .given(crisisPolicy).verifyNotCrisisClosed(session);

        assertThatThrownBy(() -> useCase.execute(USERNAME, request))
                .isSameAs(ChatbotHandler.SESSION_CRISIS_CLOSED);

        verifyNoInteractions(geminiVoiceAnalyzer);
        verifyNoInteractions(geminiChatbotClient);
    }

    @Test
    @DisplayName("STT 결과가 사전 스크리닝에 걸리면 Pro 호출을 건너뛰고 위기 안내로 세션을 닫는다")
    void preScreenCrisisSkipsProCall() throws Exception {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(1L);
        given(analysisResult.transcript()).willReturn("이제 그만 죽고 싶어요");
        given(crisisPolicy.screen(anyString())).willReturn(
                CrisisVerdict.of(CrisisTrigger.HIGH_RISK_KEYWORD, "keyword=죽고싶"));
        given(chatbotDomainService.appendMessage(
                anyString(), anyString(), any(ChatbotReply.class), any(MessageOrigin.class), any()))
                .willReturn(202L);

        VoiceReframingResponse response = useCase.execute(USERNAME, request);

        // Flash는 이미 썼지만(STT가 있어야 검사할 텍스트가 생긴다) 비싼 Pro는 건너뛴다
        verifyNoInteractions(geminiChatbotClient);
        assertThat(response.messageId()).isEqualTo(202L);
        assertThat(response.content()).isEqualTo("이제 그만 죽고 싶어요");  // 말풍선은 그대로 그린다
        assertThat(response.crisisDetected()).isTrue();
        assertThat(response.sessionClosed()).isTrue();
        assertThat(response.crisisTrigger()).isEqualTo("HIGH_RISK_KEYWORD");
        assertThat(response.analysis()).contains("109");
        verify(chatbotDomainService).closeSessionByCrisis(SESSION_ID, CrisisTrigger.HIGH_RISK_KEYWORD);
    }

    @Test
    @DisplayName("안전 필터에 차단되면 남은 턴과 무관하게 위기 안내로 세션을 닫는다")
    void safetyBlockClosesSession() throws Exception {
        givenSession();
        given(turnPolicy.verifyCanSendAndGetTurn(SESSION_ID)).willReturn(1L);
        given(turnPolicy.isFinalTurn(1L)).willReturn(false);
        given(geminiChatbotClient.generate(anyString()))
                .willReturn(GeneratedReply.safetyBlocked("finishReason=SAFETY"));
        given(crisisPolicy.inspect(any())).willReturn(
                CrisisVerdict.of(CrisisTrigger.SAFETY_BLOCKED, "finishReason=SAFETY"));

        VoiceReframingResponse response = useCase.execute(USERNAME, request);

        assertThat(response.crisisDetected()).isTrue();
        assertThat(response.sessionClosed()).isTrue();
        assertThat(response.crisisTrigger()).isEqualTo("SAFETY_BLOCKED");
        assertThat(response.detectedDistortion()).isEqualTo("위기 상황");
        verify(chatbotDomainService).closeSessionByCrisis(SESSION_ID, CrisisTrigger.SAFETY_BLOCKED);
    }
}
