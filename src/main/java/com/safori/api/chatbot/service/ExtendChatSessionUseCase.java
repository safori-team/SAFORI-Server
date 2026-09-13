package com.safori.api.chatbot.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.policy.ConversationTurnPolicy;
import com.safori.domain.chatbot.policy.CrisisGuardrailPolicy;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

/**
 * 턴을 모두 쓴 세션에서 "더 이야기하시겠어요? → 예"를 눌렀을 때 턴 제한을 해제한다.
 *
 * <p>위기로 닫힌 세션은 연장할 수 없다 — 턴 제한과 달리 안전 종료는 사용자가 풀 수 없다.
 */
@UseCase
@RequiredArgsConstructor
public class ExtendChatSessionUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final ConversationTurnPolicy turnPolicy;
    private final CrisisGuardrailPolicy crisisPolicy;

    public void execute(String username, String sessionId) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        chatbotDomainService.verifyOwnership(session, user);
        crisisPolicy.verifyNotCrisisClosed(session);

        if (session.isExtended()) {
            return;  // 더블탭·재시도 — 이미 풀려 있으니 성공으로 본다
        }
        if (!turnPolicy.isClosed(session)) {
            throw ChatbotHandler.SESSION_NOT_EXTENDABLE;
        }
        chatbotDomainService.extendSession(session.getId());
    }
}
