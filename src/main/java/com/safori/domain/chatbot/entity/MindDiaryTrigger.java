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
 * 마음일기 → 챗봇 세션 트리거 원장.
 *
 * <p>"일기 1건은 최대 한 번만 상담을 촉발한다"는 불변식을 데이터로 명시한다.
 * 이 원장이 스케줄러의 멱등성 키 소유자다 — 스캔 쿼리는 이 테이블에 행이 있으면 후보에서 제외한다.
 *
 * <p><b>왜 세션이 아니라 원장으로 멱등성을 관리하나:</b> 세션은 사용자가 삭제할 수 있는 가변
 * 자원이다. "세션이 존재하는가"로 판정하면 사용자가 세션을 지운 순간 다음 스캔에서 같은 일기가
 * 다시 후보가 되어 세션이 부활한다. 원장은 "이 일기로 트리거를 시도한 적이 있다"는 불변 사실을
 * 기록하므로, 세션이 지워져도 재생성되지 않는다.
 *
 * <p>{@link #sessionId}는 만들어진 세션을 가리킨다. 세션이 삭제되면 FK가 NULL로 바뀌지만
 * (ON DELETE SET NULL) 원장 행 자체는 남아 재생성을 막는다. 재평가를 원하면(예: 재분석으로
 * 감정이 뒤집힌 0턴 세션 폐기) 원장 행을 명시적으로 삭제한다 — 재평가가 의도된 행위가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "mind_diary_trigger",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_mdt_voice", columnNames = {"voice_id"})
        })
public class MindDiaryTrigger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mind_diary_trigger_id")
    private Long id;

    /** 트리거를 촉발한 일기. 멱등성 키(UNIQUE). 일기 삭제 시 원장도 CASCADE 삭제된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mdt_voice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Voice voice;

    /**
     * 제안 상태.
     * <ul>
     *   <li>OFFERED: 조건 충족, 사용자에게 상담 제안(모달) 대기. 세션 없음.</li>
     *   <li>ACCEPTED: 사용자가 수락 → 세션 생성됨.</li>
     *   <li>DECLINED: 사용자가 거절 → 재제안하지 않음.</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private MindDiaryTriggerStatus status;

    /** 수락 시 생성된 세션. 세션 삭제 시 NULL로 바뀌지만 원장 행은 남는다 (재생성 방지). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", columnDefinition = "CHAR(36)",
            foreignKey = @ForeignKey(name = "fk_mdt_session"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ChatSession session;

    /** 생성 근거 코드. 예) CONSECUTIVE_NEGATIVE_3D_STREAK_5. 정책 사후 분석용. */
    @Column(name = "reason", length = 64, nullable = false)
    private String reason;

    /** 조건 충족 시 제안(OFFERED) 원장 행. 세션은 사용자가 수락할 때 만들어진다. */
    public static MindDiaryTrigger offer(Voice voice, String reason) {
        return MindDiaryTrigger.builder()
                .voice(voice)
                .reason(reason)
                .status(MindDiaryTriggerStatus.OFFERED)
                .build();
    }

    /** 사용자 수락 → 생성된 세션을 연결하고 ACCEPTED로 전환. */
    public void accept(ChatSession session) {
        this.session = session;
        this.status = MindDiaryTriggerStatus.ACCEPTED;
    }

    /** 사용자 거절 → DECLINED. 재제안하지 않는다. */
    public void decline() {
        this.status = MindDiaryTriggerStatus.DECLINED;
    }

    public boolean isOffered() {
        return this.status == MindDiaryTriggerStatus.OFFERED;
    }
}
