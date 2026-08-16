package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.VoiceReframingRequest;
import com.safori.api.chatbot.dto.VoiceReframingResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.GeneratedReply;
import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.policy.CrisisGuardrailPolicy;
import com.safori.domain.chatbot.policy.CrisisVerdict;
import com.safori.domain.chatbot.model.VoiceEmotionDigest;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.chatbot.service.ChatbotMessageMapper;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.UserHonorific;
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
 *
 * <p>위기 가드레일({@link CrisisGuardrailPolicy})은 텍스트 경로와 같은 두 지점에서 가로챈다.
 * 다만 사전 스크리닝은 STT <b>이후</b>에만 가능하다(그전에는 검사할 텍스트가 없다) — 대신
 * 여기서 걸리면 비싼 Pro 호출을 건너뛴다.</p>
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
    private final CrisisGuardrailPolicy crisisPolicy;
    private final ChatbotMessageMapper mapper;
    private final GeminiChatbotClient geminiChatbotClient;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    private final GeminiEmotionMapper emotionMapper;

    public VoiceReframingResponse execute(String username, VoiceReframingRequest request) {
        // 1) 권한 검증
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        // 앞선 발화의 응답이 아직 생성 중이면 거절 — 같은 턴에 LLM을 두 번 호출하지 않는다
        if (chatbotDomainService.hasReplyInProgress(session.getId())) {
            throw ChatbotHandler.REPLY_IN_PROGRESS;
        }

        // 위기로 닫힌 세션은 턴이 남아 있어도 열리지 않는다 — 턴 검증보다 먼저
        crisisPolicy.verifyNotCrisisClosed(session);

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
        String address = UserHonorific.of(user);

        if (userInput == null || userInput.isBlank()) {
            log.info("챗봇 음성 STT 결과 없음 — 안전 응답 반환. sessionId={}", request.sessionId());
            ChatbotReply safe = ChatbotReply.safeGeneric(address);
            String safeInput = ""; // 저장용: 인식된 텍스트 없음
            Long safeId = chatbotDomainService.appendMessage(
                    session.getId(), safeInput, safe, MessageOrigin.USER_VOICE, request.voiceKey());
            return new VoiceReframingResponse(
                    safeId, safeInput,
                    safe.empathy(), safe.detectedDistortion(), safe.analysis(),
                    safe.socraticQuestion(), safe.alternativeThought(), safe.topEmotion(),
                    finalTurn, false, null);
        }

        // 3-b) 사전 스크리닝 — STT 직후, Pro 호출 전. 걸리면 비싼 상담 호출을 통째로 건너뛴다.
        CrisisVerdict preVerdict = crisisPolicy.screen(userInput);
        if (preVerdict.detected()) {
            return closeByCrisis(session, userInput, address, request.voiceKey(), preVerdict);
        }

        VoiceEmotionDigest digest = emotionMapper.toVoiceEmotionDigest(analysis, LABEL_LIMIT);

        List<HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String emotionDesc = mapper.summarizeVoiceEmotion(
                digest.topEmotion(), digest.topEmotionConfidenceBps(), digest.labels());
        String emotionHint = mapper.emotionHint(digest.topEmotion());

        String prompt = VoiceReframingPrompt.build(
                userInput, history, (int) turnCount, address,
                emotionDesc, emotionHint,
                turnPolicy.maxUserTurns(), finalTurn);

        // 4) PROCESSING 행 선(先) 커밋 — 이때부터 조회 API에 "처리 중"으로 노출된다.
        //    STT가 끝나야 userInput이 정해지므로 Flash 구간은 덮지 못하고 Pro 구간만 덮는다.
        Long messageId = chatbotDomainService.beginMessage(
                session.getId(), userInput, MessageOrigin.USER_VOICE, request.voiceKey());

        // 5) Pro — 상담 응답 (실패 시 폴백 응답 + FAILED 표시)
        GeneratedReply generated = geminiChatbotClient.generate(prompt);

        ChatbotReply reply = generated.reply();

        // 7) 응답 확정 (별도 짧은 트랜잭션) — 커밋 후 푸시 이벤트 발행.
        //    위기 응답은 생성 실패가 아니므로 COMPLETED로 남긴다.
        chatbotDomainService.settleMessage(
                messageId, reply, generated.failed());

        return new VoiceReframingResponse(
                messageId, userInput,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                finalTurn,
                false,
                null
        );
    }

    /** 사전 스크리닝에 걸린 경우 — Pro를 거치지 않고 안전 안내만 저장하고 세션을 닫는다. */
    private VoiceReframingResponse closeByCrisis(ChatSession session, String userInput,
                                                 String address, String voiceKey,
                                                 CrisisVerdict verdict) {
        ChatbotReply reply = ChatbotReply.crisis(address);
        markCrisis(session, verdict);
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), userInput, reply, MessageOrigin.USER_VOICE, voiceKey);

        return new VoiceReframingResponse(
                messageId, userInput,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                true, true, verdict.trigger().name()
        );
    }

    private void markCrisis(ChatSession session, CrisisVerdict verdict) {
        // 발화 원문은 남기지 않는다 — 민감 정보이고 메시지 행에 이미 저장돼 있다
        log.warn("CBT 상담 위기 가드레일 발동 — sessionId={}, trigger={}, detail={}",
                session.getId(), verdict.trigger(), verdict.detail());
        chatbotDomainService.closeSessionByCrisis(session.getId(), verdict.trigger());
    }
}
