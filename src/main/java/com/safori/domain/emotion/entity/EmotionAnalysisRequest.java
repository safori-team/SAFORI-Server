package com.safori.domain.emotion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.safori.domain.chatbot.entity.JsonNodeConverter;
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

import java.time.LocalDateTime;

/**
 * 소분류 감정 분석 요청 1건의 원장(ledger).
 *
 * <p>Gemini 1차 분석이 끝나면 이 행을 {@link EmotionAnalysisStatus#PENDING}으로 먼저 커밋한 뒤
 * 요청 큐로 보낸다. 응답은 다른 스레드·다른 인스턴스에서 도착할 수 있고 중복 전달될 수도 있어,
 * {@code request_id} UNIQUE + 최종 상태 확인이 멱등 처리의 기준점이 된다.
 *
 * <p>이 행이 커밋되기 전에 응답이 도착하면 매칭할 대상이 없으므로, 전송보다 저장이 반드시
 * 먼저 일어나야 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(
        name = "emotion_analysis_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ear_request_id", columnNames = "request_id")
        },
        indexes = {
                // 타임아웃 스윕: PENDING + 생성시각 범위
                @Index(name = "idx_ear_status_created", columnList = "status, createdDate"),
                @Index(name = "idx_ear_voice", columnList = "voice_id")
        }
)
public class EmotionAnalysisRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emotion_analysis_request_id")
    private Long id;

    /** 요청·응답을 잇는 추적 키. 재전송해도 새로 만들지 않는다. */
    @Column(name = "request_id", length = 64, nullable = false, updatable = false)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voice_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ear_voice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Voice voice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'PENDING'")
    private EmotionAnalysisStatus status;

    /**
     * 응답의 {@code analysis_result} 원문. 성공이면 소분류 판정 결과, 4xx 실패면 오류 본문이
     * 그대로 들어간다. 재현·품질 점검용이며 API로 노출하지 않는다.
     */
    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "analysis_result", columnDefinition = "JSON")
    private JsonNode analysisResult;

    /** S3 request.json 객체 키 (응답에서 받은 값). */
    @Column(name = "request_key", length = 512)
    private String requestKey;

    /** S3 response.json 객체 키 (응답에서 받은 값). */
    @Column(name = "response_key", length = 512)
    private String responseKey;

    /** 응답의 {@code completed_at}. 스윕으로 마감한 경우는 마감 시각. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean isFinalState() {
        return status != null && status.isFinal();
    }

    /** 분석 성공 응답 반영. */
    public void complete(JsonNode analysisResult, String requestKey, String responseKey,
                         LocalDateTime completedAt) {
        this.status = EmotionAnalysisStatus.COMPLETED;
        this.analysisResult = analysisResult;
        this.requestKey = requestKey;
        this.responseKey = responseKey;
        this.completedAt = completedAt != null ? completedAt : LocalDateTime.now();
    }

    /** 분석 Lambda 4xx 응답 반영 — 오류 본문을 그대로 보관한다. */
    public void fail(JsonNode analysisResult, String requestKey, String responseKey,
                     LocalDateTime completedAt) {
        this.status = EmotionAnalysisStatus.FAILED;
        this.analysisResult = analysisResult;
        this.requestKey = requestKey;
        this.responseKey = responseKey;
        this.completedAt = completedAt != null ? completedAt : LocalDateTime.now();
    }

    /** 큐 전송 실패 마감. */
    public void markSendFailed() {
        this.status = EmotionAnalysisStatus.SEND_FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /** 응답 미도착 마감(스윕). 이후 늦게 도착한 응답은 중복으로 간주돼 버려진다. */
    public void markTimedOut() {
        this.status = EmotionAnalysisStatus.TIMEOUT;
        this.completedAt = LocalDateTime.now();
    }
}
