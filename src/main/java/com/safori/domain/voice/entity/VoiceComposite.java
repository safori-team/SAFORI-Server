package com.safori.domain.voice.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.emotion.entity.EmotionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "voice_composite",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_vcp_voice", columnNames = {"voice_id"})
        }
)
public class VoiceComposite extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_composite_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vc_voice2")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Voice voice;

    // text sentiment (nullable)
    @Column(name = "text_score_bps")
    private Integer textScoreBps;

    @Column(name = "text_magnitude_x1000")
    private Integer textMagnitudeX1000;

    // EEG/feature weights (nullable)
    @Column(name = "alpha_bps")
    private Integer alphaBps;

    @Column(name = "beta_bps")
    private Integer betaBps;

    // composite outputs (not null)
    @Column(name = "valence_x1000", nullable = false)
    private Integer valenceX1000;

    @Column(name = "arousal_x1000", nullable = false)
    private Integer arousalX1000;

    @Column(name = "intensity_x1000", nullable = false)
    private Integer intensityX1000;

    // emotion distribution (SMALLINT UNSIGNED NOT NULL -> Integer 권장)
    @Column(name = "happy_bps", nullable = false)
    private Integer happyBps;

    @Column(name = "sad_bps", nullable = false)
    private Integer sadBps;

    @Column(name = "neutral_bps", nullable = false)
    private Integer neutralBps;

    @Column(name = "angry_bps", nullable = false)
    private Integer angryBps;

    @Column(name = "anxiety_bps", nullable = false)
    private Integer anxietyBps;

    @Column(name = "surprise_bps", nullable = false)
    private Integer surpriseBps;

    @Column(name = "top_emotion", length = 16)
    @Enumerated(EnumType.STRING)
    private EmotionType topEmotion;

    @Column(name = "top_emotion_confidence_bps")
    private Integer topEmotionConfidenceBps;

    /**
     * 도란이 말풍선 공감 텍스트 (2~3문장, 따뜻한 어투).
     * 분석 시 생성, nullable.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * 채팅 세션 리스트 제목 (15자 이내 명사형 한 줄).
     * 분석 시 생성, nullable.
     */
    @Column(name = "title", length = 15)
    private String title;

    /**
     * 대표 감정을 강제로 덮어쓴다. 개발용 시딩 API 전용 — 실제 분석 내용(요약/라벨/전사)은
     * 그대로 두고 스케줄러 스트릭 테스트를 위해 topEmotion만 결정적으로 바꾼다.
     */
    public void overrideTopEmotion(EmotionType topEmotion) {
        this.topEmotion = topEmotion;
    }
}
