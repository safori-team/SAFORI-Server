package com.safori.domain.chatbot.repository;

import com.safori.domain.chatbot.entity.ChatSessionDiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionDiaryRepository extends JpaRepository<ChatSessionDiary, Long> {

    List<ChatSessionDiary> findBySession_IdOrderBySeqAsc(String sessionId);
}
