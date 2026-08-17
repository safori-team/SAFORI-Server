package com.safori.domain.chatbot.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_last_message",
               columnList = "user_id, lastMessageAt DESC")
})
public class ChatSession extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 마지막으로 메시지가 추가된 시각. 세션 목록 정렬 기준.
     * BaseTimeEntity의 {@code lastModifiedDate}와 별개로 메시지 INSERT마다 명시적 갱신.
     */
    @Column
    private LocalDateTime lastMessageAt;

    /**
     * 가드레일이 이 세션을 중단시킨 신호. null이면 위기로 닫히지 않은 정상 세션이다.
     *
     * <p>턴 소진 종료는 메시지 수로 계산되지만(ConversationTurnPolicy) 위기 종료는 계산으로
     * 복원할 수 없다 — 2턴째에 닫힐 수도 있기 때문. 그래서 세션에 상태로 남긴다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "crisis_trigger", length = 24)
    private CrisisTrigger crisisTrigger;

    /** 가드레일이 발동한 시각. 위기 종료가 아니면 null. */
    @Column(name = "crisis_detected_at")
    private LocalDateTime crisisDetectedAt;

    /**
     * 세션 생성. 사용자가 직접 열든, 마음일기 트리거로 열든 세션 자체는 동일하다.
     * "이 세션이 어떤 일기로 왜 생겼는지"는 {@code mind_diary_trigger} 원장이 소유한다.
     */
    public static ChatSession create(User user) {
        return ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .lastMessageAt(LocalDateTime.now())
                .build();
    }

    /** 메시지 추가 시 호출. 세션 정렬 기준을 갱신한다. */
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }

    /**
     * 가드레일 발동으로 상담을 중단한다. 남은 턴이 있어도 이후 발화는 받지 않는다.
     *
     * <p>먼저 잡힌 신호를 유지한다 — 한 번 위기로 닫힌 세션은 다시 열리지 않으므로
     * 최초 발동 원인이 사후 분석에 더 쓸모 있다.
     */
    public void closeByCrisis(CrisisTrigger trigger) {
        if (this.crisisTrigger != null) {
            return;
        }
        this.crisisTrigger = trigger;
        this.crisisDetectedAt = LocalDateTime.now();
    }

    /** 가드레일에 걸려 중단된 세션인지. */
    public boolean isCrisisClosed() {
        return this.crisisTrigger != null;
    }
}
