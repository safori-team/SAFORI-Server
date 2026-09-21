package com.safori.domain.chatbot.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.voice.entity.Voice;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 세션이 어떤 마음일기들을 근거로 만들어졌는지 기록한다 (세션 N:M 일기).
 *
 * <p>연속 부정 감정 정책에서는 세션 하나가 여러 날짜의 일기를 컨텍스트로 갖고,
 * 일기 하나가 여러 세션에 걸쳐 쓰일 수 있다(수요일 일기가 수·목·금 세션의 컨텍스트).
 * 트리거 일기 자체는 {@link ChatSession#getTriggerVoiceId()}에 따로 있고,
 * 여기엔 트리거 일기를 포함한 컨텍스트 전체가 들어간다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_session_diary",
        indexes = {
                @Index(name = "idx_csd_voice", columnList = "voice_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_csd_session_voice",
                                  columnNames = {"session_id", "voice_id"})
        })
public class ChatSessionDiary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_session_diary_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_csd_session"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_csd_voice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Voice voice;

    /** 컨텍스트 내 순서. 0부터 시간순(오래된 → 최신), 마지막이 트리거 일기. */
    @Column(name = "seq", nullable = false)
    private int seq;

    public static ChatSessionDiary of(ChatSession session, Voice voice, int seq) {
        return ChatSessionDiary.builder()
                .session(session)
                .voice(voice)
                .seq(seq)
                .build();
    }
}
