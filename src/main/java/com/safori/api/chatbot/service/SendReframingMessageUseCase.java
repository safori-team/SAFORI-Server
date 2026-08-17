package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.ReframingRequest;
import com.safori.api.chatbot.dto.ReframingResponse;
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
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.prompts.ReframingPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 텍스트 채팅. 트랜잭션을 세 단계로 분리한다 (LLM 호출이 DB 커넥션을 점유하지 않도록):
 * <ol>
 *   <li>ConversationTurnPolicy 턴 검증 + ChatbotDomainService.loadRecentHistory (readOnly 트랜잭션)</li>
 *   <li>ChatbotDomainService.beginMessage — PROCESSING 행을 먼저 커밋 (Transactional)</li>
 *   <li>(트랜잭션 밖) GeminiChatbotClient.generate</li>
 *   <li>ChatbotDomainService.settleMessage — COMPLETED/FAILED 확정 (Transactional)</li>
 * </ol>
 *
 * <p>2단계 커밋이 있어야 응답을 기다리는 동안 사용자가 화면을 벗어나 목록/상세를 조회했을 때
 * "처리 중"을 볼 수 있다. 응답 형상은 기존과 동일하다 — 여전히 완성된 응답을 한 번에 반환한다.
 *
 * <p>위기 가드레일({@link CrisisGuardrailPolicy})은 상담 LLM 호출 전에 키워드·문맥을 분류한다.
 * 여기서 위기로 확정된 경우에만 안전 안내로 갈아끼우고 세션을 영구 종료한다.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final ConversationTurnPolicy turnPolicy;
    private final CrisisGuardrailPolicy crisisPolicy;
    private final GeminiChatbotClient geminiChatbotClient;

    public ReframingResponse execute(String username, ReframingRequest request) {
        // 1) 권한 검증 + 컨텍스트 로딩 (별도 readOnly 트랜잭션 경계)
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        // 앞선 발화의 응답이 아직 생성 중이면 거절 — 같은 턴에 LLM을 두 번 호출하지 않는다
        if (chatbotDomainService.hasReplyInProgress(session.getId())) {
            throw ChatbotHandler.REPLY_IN_PROGRESS;
        }

        // 위기로 닫힌 세션은 턴이 남아 있어도 열리지 않는다 — 턴 검증보다 먼저
        crisisPolicy.verifyNotCrisisClosed(session);

        // 턴 소진 시 여기서 끊는다 — 종료된 세션에 LLM 토큰을 쓰지 않는다
        long turnCount = turnPolicy.verifyCanSendAndGetTurn(session.getId());
        boolean finalTurn = turnPolicy.isFinalTurn(turnCount);

        String address = UserHonorific.of(user);

        // 2) 사전 스크리닝 — 걸리면 LLM을 아예 태우지 않는다.
        //    위험 발화를 모델에 보내지 않고, 토큰도 쓰지 않으며, 응답이 안전 안내임이 확정된다.
        CrisisVerdict preVerdict = crisisPolicy.screen(request.userInput());
        if (preVerdict.detected()) {
            return closeByCrisis(session, request.userInput(), address, preVerdict);
        }

        List<HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion(),
                address, turnPolicy.maxUserTurns(), finalTurn);

        // 3) PROCESSING 행 선(先) 커밋 — 이때부터 조회 API에 "처리 중"으로 노출된다
        Long messageId = chatbotDomainService.beginMessage(
                session.getId(), request.userInput(), MessageOrigin.USER_TEXT, null);

        // 4) LLM 호출 (트랜잭션 밖)
        GeneratedReply generated = geminiChatbotClient.generate(prompt);

        ChatbotReply reply = generated.reply();

        // 6) 응답 확정 (별도 짧은 트랜잭션) — 커밋 후 푸시 이벤트 발행.
        //    위기 응답은 생성 실패가 아니므로 COMPLETED로 남긴다(재시도 대상이 아니다).
        chatbotDomainService.settleMessage(
                messageId, reply, generated.failed());

        return new ReframingResponse(
                messageId,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                finalTurn,
                false,
                null
        );
    }

    /** 사전 스크리닝에 걸린 경우 — LLM을 거치지 않고 안전 안내만 저장하고 세션을 닫는다. */
    private ReframingResponse closeByCrisis(ChatSession session, String userInput,
                                            String address, CrisisVerdict verdict) {
        ChatbotReply reply = ChatbotReply.crisis(address);
        markCrisis(session, verdict);
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), userInput, reply, MessageOrigin.USER_TEXT, null);

        return new ReframingResponse(
                messageId,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                true,
                true,
                verdict.trigger().name()
        );
    }

    private void markCrisis(ChatSession session, CrisisVerdict verdict) {
        // 발화 원문은 남기지 않는다 — 민감 정보이고 메시지 행에 이미 저장돼 있다
        log.warn("CBT 상담 위기 가드레일 발동 — sessionId={}, trigger={}, detail={}",
                session.getId(), verdict.trigger(), verdict.detail());
        chatbotDomainService.closeSessionByCrisis(session.getId(), verdict.trigger());
    }
}
