package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.FeedbackRequest;
import com.safori.api.chatbot.dto.FeedbackResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.DoranEmotion;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class UpdateMessageFeedbackUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    @Transactional
    public FeedbackResponse execute(String username, Long messageId, FeedbackRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        // fetch join — verifyOwnership에서 lazy 추가 쿼리 2회 방지
        ChatMessage message = chatMessageAdaptor.queryByIdWithSessionAndUser(messageId);
        chatbotDomainService.verifyOwnership(message, user);

        DoranEmotion emotion = chatbotDomainService.parseFeedbackEmotion(request.emotion());
        message.applyFeedback(emotion, request.detail());

        return new FeedbackResponse(
                message.getId(),
                emotion.getCode(),
                message.getFeedbackDetail(),
                message.getFeedbackAt()
        );
    }
}
