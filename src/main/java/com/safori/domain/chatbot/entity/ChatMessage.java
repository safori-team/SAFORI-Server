package com.safori.domain.chatbot.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.voice.entity.Voice;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_created",
               columnList = "session_id, createdDate DESC"),
        @Index(name = "idx_chat_message_voice", columnList = "voice_id"),
        // 좀비 정리 스케줄러: 미완료 상태 + 생성 시각 범위 (1분마다 실행)
        @Index(name = "idx_chat_message_reply_status", columnList = "reply_status, createdDate")
})
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_chat_message_session"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String userInput;

    /**
     * 도란이 응답 JSON. {@link ChatReplyStatus#PROCESSING} 구간에는 아직 응답이 없어 null이다.
     * (LLM 호출 전에 행을 먼저 커밋하기 때문 — {@link ChatReplyStatus} 참조)
     */
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "bot_response", columnDefinition = "JSON")
    private JsonNode botResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_status", nullable = false,
            columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'")
    private ChatReplyStatus replyStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_id")
    private Voice voice;

    /**
     * 챗봇 음성 입력(USER_VOICE)의 S3 오디오 키. 히스토리에서 사용자 발화를 다시 듣기 위한 참조.
     * 텍스트/마음일기 메시지에서는 null.
     */
    @Column(name = "voice_key")
    private String voiceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DoranEmotion feedbackEmotion;

    @Column(columnDefinition = "TEXT")
    private String feedbackDetail;

    @Column
    private LocalDateTime feedbackAt;

    /** LLM 호출이 끝난 뒤 응답과 최종 상태를 기록한다. 실패면 폴백 응답이 들어온다. */
    public void settle(JsonNode botResponse, boolean failed) {
        this.botResponse = botResponse;
        this.replyStatus = failed ? ChatReplyStatus.FAILED : ChatReplyStatus.COMPLETED;
    }

    public void applyFeedback(DoranEmotion emotion, String detail) {
        this.feedbackEmotion = emotion;
        this.feedbackDetail = detail;
        this.feedbackAt = LocalDateTime.now();
    }
}
