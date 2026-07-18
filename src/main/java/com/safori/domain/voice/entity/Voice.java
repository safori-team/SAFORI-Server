package com.safori.domain.voice.entity;

import com.safori.domain.common.entity.BaseTimeEntity;
import com.safori.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "voice", indexes = {
        // 세션 트리거 스케줄러: 상태 필터 + 분석 완료 시각 범위/정렬 (10분마다 실행)
        @Index(name = "idx_voice_status_completed",
               columnList = "analysisStatus, analysisCompletedAt"),
        // 연속 감정 판정 + 주간/월간 리포트: 사용자별 작성일 범위 조회
        @Index(name = "idx_voice_user_created", columnList = "user_id, createdDate")
})
public class Voice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_id")
    private Long id;

    private String voiceKey;
    private String voiceTitle;
    private int duration;
    private int sampleRate;
    private int bitRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'")
    private AnalysisStatus analysisStatus;

    private LocalDateTime analysisCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public void markAnalysisPending() {
        this.analysisStatus = AnalysisStatus.PENDING;
    }

    public void markAnalysisCompleted() {
        this.analysisStatus = AnalysisStatus.COMPLETED;
        this.analysisCompletedAt = LocalDateTime.now();
    }

    public void markAnalysisProcessing() {
        this.analysisStatus = AnalysisStatus.PROCESSING;
        this.analysisCompletedAt = null;
    }

    public void markAnalysisFailed() {
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    public enum AnalysisStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
