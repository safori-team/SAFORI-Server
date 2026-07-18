package com.safori.domain.chatbot.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatSessionAdaptorImpl implements ChatSessionAdaptor {

    private final ChatSessionRepository repository;

    @Override
    public ChatSession queryById(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> ChatbotHandler.SESSION_NOT_FOUND);
    }

    @Override
    public Page<ChatSession> queryByUsername(String username, Pageable pageable) {
        return repository.findByUser_UsernameOrderByLastMessageAtDesc(username, pageable);
    }

    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }

    @Override
    @Transactional
    public void delete(ChatSession session) {
        repository.delete(session);
    }
}
