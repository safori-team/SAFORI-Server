package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.ChatSessionItemResponse;
import com.safori.api.common.dto.PagedResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.policy.ConversationLimitProperties;
import com.safori.domain.chatbot.service.ChatbotMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

@UseCase
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ConversationLimitProperties conversationProps;
    private final ChatbotMessageMapper mapper;

    public PagedResponse<ChatSessionItemResponse> execute(String username, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Page<ChatSession> sessionPage = chatSessionAdaptor.queryByUsername(
                username, PageRequest.of(page - 1, size));

        // N+1 제거: 페이지 내 모든 세션의 최신 메시지를 한 번에 조회
        List<String> sessionIds = sessionPage.getContent().stream().map(ChatSession::getId).toList();
        if (sessionIds.isEmpty()) {
            return PagedResponse.from(sessionPage.map(s -> toItem(s, null, 0L)));
        }
        Map<String, ChatMessage> latestBySession = chatMessageAdaptor.queryLatestBySessionIds(sessionIds);
        Map<String, Long> userTurnsBySession = chatMessageAdaptor.countUserTurnsBySessionIds(sessionIds);

        Page<ChatSessionItemResponse> mapped = sessionPage.map(s -> toItem(
                s, latestBySession.get(s.getId()),
                userTurnsBySession.getOrDefault(s.getId(), 0L)));
        return PagedResponse.from(mapped);
    }

    private ChatSessionItemResponse toItem(ChatSession s, ChatMessage latest, long userTurns) {
        String lastMessage = latest == null ? null : latest.getUserInput();
        // 마음일기 트리거(USER 발화 없음) → 봇 empathy 첫 줄을 미리보기로 fallback
        if (lastMessage == null && latest != null) {
            lastMessage = mapper.botMessagePreview(latest.getBotResponse());
        }

        String emotion = latest == null ? null : mapper.emotion(latest.getBotResponse());
        if (latest != null && latest.getFeedbackEmotion() != null) {
            emotion = latest.getFeedbackEmotion().getCode();
        }

        // 턴 소진과 위기 종료는 별개 경로다 — 위기는 남은 턴과 무관하게 세션을 닫는다
        boolean crisisClosed = s.isCrisisClosed();

        return new ChatSessionItemResponse(
                s.getId(),
                lastMessage,
                s.getLastMessageAt(),
                latest == null ? List.of() : mapper.distortionTags(latest.getBotResponse()),
                emotion,
                userTurns >= conversationProps.getMaxUserTurns() || crisisClosed,
                crisisClosed,
                // 최신 메시지가 이미 조회돼 있어 추가 쿼리 없이 상태를 낸다
                latest == null ? null : latest.getReplyStatus()
        );
    }
}
