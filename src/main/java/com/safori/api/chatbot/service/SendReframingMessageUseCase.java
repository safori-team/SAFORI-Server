package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.ReframingRequest;
import com.safori.api.chatbot.dto.ReframingResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.prompts.ReframingPrompt;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 텍스트 채팅. 트랜잭션을 두 단계로 분리한다 (LLM 호출이 DB 커넥션을 점유하지 않도록):
 * <ol>
 *   <li>ConversationTurnPolicy 턴 검증 + ChatbotDomainService.loadRecentHistory (readOnly 트랜잭션)</li>
 *   <li>(트랜잭션 밖) GeminiChatbotClient.generate</li>
 *   <li>ChatbotDomainService.appendMessage (Transactional)</li>
 * </ol>
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

        // 턴 소진 시 여기서 끊는다 — 종료된 세션에 LLM 토큰을 쓰지 않는다
        long turnCount = turnPolicy.verifyCanSendAndGetTurn(session.getId());
        boolean finalTurn = turnPolicy.isFinalTurn(turnCount);

        List<HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion(),
                turnPolicy.maxUserTurns(), finalTurn);

        // 2) LLM 호출 (트랜잭션 밖) — infra→domain 변환
        ChatbotReply reply = geminiChatbotClient.generate(prompt).toReply();

        // 3) 메시지 INSERT + 세션 touch (별도 짧은 트랜잭션)
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), request.userInput(), reply, MessageOrigin.USER_TEXT, null);

        return new ReframingResponse(
                messageId,
                reply.empathy(), reply.detectedDistortion(), reply.analysis(),
                reply.socraticQuestion(), reply.alternativeThought(), reply.topEmotion(),
                finalTurn
        );
    }
}
