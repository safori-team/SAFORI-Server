package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.VoiceReframingRequest;
import com.safori.api.chatbot.dto.VoiceReframingResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.model.VoiceEmotionDigest;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.chatbot.service.ChatbotMessageMapper;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.GeminiEmotionMapper;
import com.safori.infra.ai.gemini.GeminiVoiceAnalyzer;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.ai.gemini.prompts.VoiceReframingPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 음성 채팅 — 단일 호출로 전체 파이프라인을 처리한다.
 *
 * <pre>
 *   voiceKey ─▶ Gemini Flash(STT + 감정 분석) ─▶ Gemini Pro(상담) ─▶ 메시지 저장 ─▶ 응답
 * </pre>
 *
 * <p>마음일기 Voice와 분리된 경량 경로 — {@code VoiceAnalysisCompletedEvent}를 발행하지 않으므로
 * 의도치 않은 채팅 세션 자동 생성 부수효과가 없다. STT 텍스트는 {@code ChatMessage.userInput}에,
 * 재생용 {@code voiceKey}는 {@code ChatMessage.voiceKey}에 저장된다. 감정 분석값은 상담 프롬프트에만
 * 쓰이고 영구 저장하지 않는다.</p>
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendVoiceReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;
    private static final int LABEL_LIMIT = 5;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final ConversationTurnPolicy turnPolicy;
    private final ChatbotMessageMapper mapper;
    private final GeminiChatbotClient geminiChatbotClient;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    private final GeminiEmotionMapper emotionMapper;

    public VoiceReframingResponse execute(String username, VoiceReframingRequest request) {
        // 1) 권한 검증
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        // 2) 턴 검증 — STT보다 먼저. 종료된 세션에 Flash/Pro 호출을 쓰지 않는다.
        long turnCount = turnPolicy.verifyCanSendAndGetTurn(session.getId());
        boolean finalTurn = turnPolicy.isFinalTurn(turnCount);

        // 3) Flash — STT + 감정 분석. 음성이 유일 입력이므로 실패 시 폴백 없이 에러.
        GeminiAnalysisResult analysis;
        try {
            analysis = geminiVoiceAnalyzer.transcribeAndAnalyze(request.voiceKey());
        } catch (Exception e) {
            log.error("챗봇 음성 STT 실패 - sessionId={}, voiceKey={}",
                    request.sessionId(), request.voiceKey(), e);
            throw ChatbotHandler.VOICE_STT_FAILED;
        }
        String userInput = analysis.transcript();
        VoiceEmotionDigest digest = emotionMapper.toVoiceEmotionDigest(analysis, LABEL_LIMIT);

        List<HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String emotionDesc = mapper.summarizeVoiceEmotion(
                digest.topEmotion(), digest.topEmotionConfidenceBps(), digest.labels());
        String emotionHint = mapper.emotionHint(digest.topEmotion());

        String prompt = VoiceReframingPrompt.build(
                userInput, history, (int) turnCount,
                user.getName() == null ? "내담자" : user.getName(),
                emotionDesc, emotionHint,
                turnPolicy.maxUserTurns(), finalTurn);

        // 4) Pro — 상담 응답 (infra→domain 변환, 실패 시 폴백 응답)
        ChatbotReply reply = geminiChatbotClient.generate(prompt).toReply();

        // 5) 메시지 저장 (userInput=STT, voiceKey=재생용)
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), userInput, reply, MessageOrigin.USER_VOICE, request.voiceKey());

        return new VoiceReframingResponse(
                messageId, userInput,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                finalTurn
        );
    }
}
