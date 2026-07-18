package com.safori.domain.chatbot.repository;

import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.MessageOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findBySession_IdOrderByCreatedDateDesc(String sessionId, Pageable pageable);

    long countBySession_Id(String sessionId);

    /** 사용자 발화 수. MIND_DIARY 트리거 메시지는 userInput이 null이라 제외된다. */
    long countBySession_IdAndUserInputIsNotNull(String sessionId);

    /**
     * 여러 세션의 사용자 발화 수를 한 번에 조회 (N+1 방지).
     * 반환 컬럼: [0] sessionId(String), [1] userTurns(Long).
     * 발화가 0건인 세션은 결과에 없다.
     */
    @Query("""
            SELECT m.session.id, COUNT(m)
            FROM ChatMessage m
            WHERE m.session.id IN :sessionIds
              AND m.userInput IS NOT NULL
            GROUP BY m.session.id
            """)
    List<Object[]> countUserTurnsBySessionIds(@Param("sessionIds") List<String> sessionIds);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdDate DESC, m.id DESC")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    Optional<ChatMessage> findTopBySession_IdOrderByCreatedDateDescIdDesc(String sessionId);

    /**
     * 권한 검증용 fetch join — message → session → user를 한 번의 쿼리로.
     * verifyOwnership에서 lazy 추가 쿼리 2회를 방지.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.session s
            JOIN FETCH s.user
            WHERE m.id = :messageId
            """)
    Optional<ChatMessage> findByIdWithSessionAndUser(@Param("messageId") Long messageId);

    /** 마음일기 트리거 메시지 단건 (voiceId + MIND_DIARY origin). */
    Optional<ChatMessage> findByVoice_IdAndOrigin(Long voiceId, MessageOrigin origin);

    /** 마음일기 트리거 메시지 일괄 조회. */
    List<ChatMessage> findByVoice_IdInAndOrigin(List<Long> voiceIds, MessageOrigin origin);

    /**
     * 여러 세션의 최신 메시지를 한 번에 조회 (N+1 방지).
     * createdDate 동률 시 id로 2차 비교해 결정적 최신 메시지를 반환.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.session.id IN :sessionIds
              AND NOT EXISTS (
                  SELECT 1 FROM ChatMessage m2
                  WHERE m2.session.id = m.session.id
                    AND (
                      m2.createdDate > m.createdDate
                      OR (m2.createdDate = m.createdDate AND m2.id > m.id)
                    )
              )
            """)
    List<ChatMessage> findLatestBySessionIds(@Param("sessionIds") List<String> sessionIds);
}
