package com.safori.domain.voice.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 사용자가 AI 감정 분석 결과가 잘못됐다고 신고한 내역.
 * voice 1개당 user 1개의 신고만 허용 (재신고 시 덮어쓰기).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "voice_emotion_report",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ver_voice_user", columnNames = {"voice_id", "user_id"})
        }
)
public class VoiceEmotionReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_emotion_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ver_voice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Voice voice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ver_user"))
    private User user;

    /** 사용자가 선택한 실제 감정 */
    @Enumerated(EnumType.STRING)
    @Column(name = "reported_emotion", length = 16, nullable = false)
    private EmotionType reportedEmotion;

    /** 상세 설명 (선택 입력) */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    public void update(EmotionType reportedEmotion, String message) {
        this.reportedEmotion = reportedEmotion;
        this.message = message;
    }
}
