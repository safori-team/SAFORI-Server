package com.safori.domain.chatbot.adaptor;

import com.safori.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatMessageAdaptor {
    ChatMessage queryById(Long messageId);

    /** 권한 검증을 위해 message → session → user를 fetch join으로 한 번에 조회. */
    ChatMessage queryByIdWithSessionAndUser(Long messageId);

    Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable);
    List<ChatMessage> queryRecentBySessionId(String sessionId, int limit);
    long countBySessionId(String sessionId);

    /** 사용자 발화 수 (대화 턴 제한 판정용). 봇이 먼저 건 말은 세지 않는다. */
    long countUserTurnsBySessionId(String sessionId);

    /** 여러 세션의 사용자 발화 수 일괄 조회 — N+1 방지. 발화 0건인 세션은 0으로 채워 반환. */
    Map<String, Long> countUserTurnsBySessionIds(List<String> sessionIds);

    /** 세션에 응답 생성 중(PROCESSING)인 메시지가 있는지. */
    boolean existsProcessingBySessionId(String sessionId);

    /** {@code threshold} 이전에 만들어졌는데 아직 PROCESSING인 메시지 (좀비 정리용). */
    List<ChatMessage> queryStaleProcessing(LocalDateTime threshold);
    Optional<ChatMessage> queryLatestBySessionId(String sessionId);

    /** 여러 세션의 최신 메시지 한 번에 조회 — N+1 방지. */
    Map<String, ChatMessage> queryLatestBySessionIds(List<String> sessionIds);

    /** 마음일기 트리거 메시지 단건 조회 (voiceId 기준). 없으면 empty. */
    Optional<ChatMessage> findMindDiaryMessageByVoiceId(Long voiceId);

    /** 마음일기 트리거 메시지 일괄 조회 — N+1 방지. */
    Map<Long, ChatMessage> findMindDiaryMessagesByVoiceIds(List<Long> voiceIds);

    ChatMessage save(ChatMessage message);
}
