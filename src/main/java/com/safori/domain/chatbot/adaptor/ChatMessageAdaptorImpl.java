package com.safori.domain.chatbot.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.ChatReplyStatus;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageAdaptorImpl implements ChatMessageAdaptor {

    private final ChatMessageRepository repository;

    @Override
    public ChatMessage queryById(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> ChatbotHandler.MESSAGE_NOT_FOUND);
    }

    @Override
    public ChatMessage queryByIdWithSessionAndUser(Long messageId) {
        return repository.findByIdWithSessionAndUser(messageId)
                .orElseThrow(() -> ChatbotHandler.MESSAGE_NOT_FOUND);
    }

    @Override
    public Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable) {
        return repository.findBySession_IdOrderByCreatedDateDesc(sessionId, pageable);
    }

    @Override
    public List<ChatMessage> queryRecentBySessionId(String sessionId, int limit) {
        return repository.findRecentBySessionId(sessionId, PageRequest.of(0, limit));
    }

    @Override
    public long countBySessionId(String sessionId) {
        return repository.countBySession_Id(sessionId);
    }

    @Override
    public long countUserTurnsBySessionId(String sessionId) {
        return repository.countBySession_IdAndUserInputIsNotNullAndReplyStatusNot(
                sessionId, ChatReplyStatus.FAILED);
    }

    @Override
    public boolean existsProcessingBySessionId(String sessionId) {
        return repository.existsBySession_IdAndReplyStatus(sessionId, ChatReplyStatus.PROCESSING);
    }

    @Override
    public List<ChatMessage> queryStaleProcessing(LocalDateTime threshold) {
        return repository.findByReplyStatusAndCreatedDateBefore(
                ChatReplyStatus.PROCESSING, threshold);
    }

    @Override
    public Map<String, Long> countUserTurnsBySessionIds(List<String> sessionIds) {
        Map<String, Long> counts = new HashMap<>();
        for (String id : sessionIds) counts.put(id, 0L);  // 발화 0건 세션도 키를 갖도록
        for (Object[] row : repository.countUserTurnsBySessionIds(sessionIds)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Override
    public Optional<ChatMessage> queryLatestBySessionId(String sessionId) {
        return repository.findTopBySession_IdOrderByCreatedDateDescIdDesc(sessionId);
    }

    @Override
    public Map<String, ChatMessage> queryLatestBySessionIds(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return new HashMap<>();
        return repository.findLatestBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        m -> m.getSession().getId(),
                        Function.identity(),
                        // 같은 세션에 동시각 메시지가 두 건 있으면 PK 큰(최신) 것을 채택
                        (a, b) -> a.getId() > b.getId() ? a : b
                ));
    }

    @Override
    public Optional<ChatMessage> findMindDiaryMessageByVoiceId(Long voiceId) {
        return repository.findByVoice_IdAndOrigin(voiceId, MessageOrigin.MIND_DIARY);
    }

    @Override
    public Map<Long, ChatMessage> findMindDiaryMessagesByVoiceIds(List<Long> voiceIds) {
        if (voiceIds == null || voiceIds.isEmpty()) return new HashMap<>();
        return repository.findByVoice_IdInAndOrigin(voiceIds, MessageOrigin.MIND_DIARY).stream()
                .collect(Collectors.toMap(
                        m -> m.getVoice().getId(),
                        Function.identity()
                ));
    }

    @Override
    @Transactional
    public ChatMessage save(ChatMessage message) {
        return repository.save(message);
    }
}
