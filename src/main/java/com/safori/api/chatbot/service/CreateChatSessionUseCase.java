package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.CreateSessionResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class CreateChatSessionUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;

    public CreateSessionResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = ChatSession.create(user);
        ChatSession saved = chatSessionAdaptor.save(session);
        return new CreateSessionResponse(saved.getId());
    }
}
