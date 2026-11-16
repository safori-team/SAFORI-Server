package com.safori.domain.chatbot.repository;

import com.safori.domain.chatbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    Page<ChatSession> findByUser_UsernameOrderByLastMessageAtDesc(String username, Pageable pageable);

    /** 대상자 판정(추가 상담 반복 이용): 이 시각 이후 최신 상담 3회. */
    List<ChatSession> findTop3ByUser_IdAndCreatedDateGreaterThanEqualOrderByCreatedDateDesc(Long userId,
                                                                                         LocalDateTime since);
}

