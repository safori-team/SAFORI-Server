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
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.prompts.ReframingPrompt;
import lombok.RequiredArgsConstructor;

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
 */
@UseCase
@RequiredArgsConstructor
public class SendReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final ConversationTurnPolicy turnPolicy;
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

        // 턴 소진 시 여기서 끊는다 — 종료된 세션에 LLM 토큰을 쓰지 않는다
        long turnCount = turnPolicy.verifyCanSendAndGetTurn(session.getId());
        boolean finalTurn = turnPolicy.isFinalTurn(turnCount);

        List<HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String address = UserHonorific.of(user);
        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion(),
                address, turnPolicy.maxUserTurns(), finalTurn);

        // 2) PROCESSING 행 선(先) 커밋 — 이때부터 조회 API에 "처리 중"으로 노출된다
        Long messageId = chatbotDomainService.beginMessage(
                session.getId(), request.userInput(), MessageOrigin.USER_TEXT, null);

        // 3) LLM 호출 (트랜잭션 밖)
        GeneratedReply generated = geminiChatbotClient.generate(prompt);
        ChatbotReply reply = generated.reply();

        // 4) 응답 확정 (별도 짧은 트랜잭션) — 커밋 후 푸시 이벤트 발행
        chatbotDomainService.settleMessage(messageId, reply, generated.failed());

        return new ReframingResponse(
                messageId,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                finalTurn
        );
    }
}
