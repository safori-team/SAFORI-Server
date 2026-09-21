package com.safori.infra.sqs.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 소분류 감정 분석 비동기 파이프라인 설정.
 *
 * <p>큐 URL이 비어 있으면 파이프라인이 통째로 비활성화되고 Gemini 소분류로 즉시 분석을
 * 마감한다(로컬·테스트·문서 생성 프로파일의 기본값). 운영 큐를 로컬에서 켜면 응답 메시지를
 * 가로채게 되므로 기본값을 비워 둔다.
 *
 * <p>env override 예: {@code SAFORI_EMOTION_ANALYSIS_REQUEST_QUEUE_URL=...}
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "safori.emotion-analysis")
public class EmotionAnalysisSqsProperties {

    /** 요청 큐 URL. 비우면 요청 전송 비활성. */
    private String requestQueueUrl = "";

    /** 응답 큐 URL. 비우면 응답 수신 비활성. */
    private String responseQueueUrl = "";

    /** Long Polling 대기 시간(초). SQS 상한이 20초. */
    @Min(0)
    @Max(20)
    private int pollWaitSeconds = 20;

    /** 한 번의 ReceiveMessage로 가져올 최대 메시지 수. SQS 상한이 10건. */
    @Min(1)
    @Max(10)
    private int pollMaxMessages = 10;

    /**
     * 이 시간이 지나도 응답이 없으면 스윕이 요청을 마감한다.
     *
     * <p>분석 Lambda 5xx·타임아웃은 응답 큐에 아무 것도 넣지 않으므로(요청 큐에서 재시도),
     * 이 값이 없으면 일기가 영원히 분석 중으로 남는다. 재시도까지 기다리는 시간과 사용자가
     * 완료 푸시를 기다리는 시간의 절충값이다.
     */
    @NotNull
    private Duration pendingTimeout = Duration.ofMinutes(10);

    /** 요청 전송 활성 여부. */
    public boolean sendEnabled() {
        return requestQueueUrl != null && !requestQueueUrl.isBlank();
    }

    /** 응답 수신 활성 여부. */
    public boolean receiveEnabled() {
        return responseQueueUrl != null && !responseQueueUrl.isBlank();
    }
}
