package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.infra.sqs.config.EmotionAnalysisSqsProperties;
import com.safori.infra.sqs.dto.EmotionAnalysisResponseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 응답 큐를 직접 폴링해 소분류 판정 결과를 반영한다.
 *
 * <p>메시지 삭제는 DB 반영이 커밋된 뒤에만 한다. 실패하면 삭제하지 않아 Visibility Timeout
 * 뒤 재수신되고, 중복 전달은 원장의 최종 상태 확인이 흡수한다.
 *
 * <p>전용 스레드를 쓴다. {@code @Scheduled}로 붙이면 20초 Long Polling이 공용 스케줄러
 * 스레드(기본 1개)를 점유해 마음일기 제안·리포트 스케줄러를 굶긴다.
 *
 * <p>응답 큐에 DLQ가 없으므로 <b>재수신해도 처리할 수 없는 메시지는 여기서 폐기한다</b>
 * (역직렬화 실패, 계약 밖 {@code processing_status}, 원장에 없는 {@code request_id}).
 * 남겨두면 보관 기간 내내 같은 메시지가 되돌아올 뿐이고, 원문은 S3 {@code response_key}에
 * 남아 있다. 반대로 DB 장애처럼 <b>복구되면 처리 가능한</b> 실패는 삭제하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${safori.emotion-analysis.response-queue-url:}'.isEmpty()")
public class EmotionAnalysisResponsePoller implements SmartLifecycle {

    /** 수신 자체가 실패했을 때(자격증명·네트워크) 바쁜 루프를 막는 대기 시간. */
    private static final long RECEIVE_ERROR_BACKOFF_MS = 5_000L;

    /** 폐기 메시지 본문을 로그에 남길 최대 길이. */
    private static final int POISON_LOG_BODY_LIMIT = 2_000;

    /** 수신 실패가 이어질 때 ERROR 로그를 남길 주기(5초 백오프 × 60 ≈ 5분). */
    private static final int RECEIVE_ERROR_LOG_EVERY = 60;

    private final SqsClient sqsClient;
    private final EmotionAnalysisSqsProperties properties;
    private final EmotionAnalysisResultApplier resultApplier;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    /** 폴러 스레드에서만 읽고 쓴다. */
    private int receiveFailures = 0;

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = new Thread(this::pollLoop, "emotion-analysis-response-poller");
        worker.setDaemon(true);
        worker.start();
        log.info("소분류 감정 분석 응답 폴러 시작 — queue={}", properties.getResponseQueueUrl());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (worker != null) worker.interrupt();
        log.info("소분류 감정 분석 응답 폴러 종료");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                receiveOnce().forEach(this::handle);
                receiveFailures = 0;
            } catch (Exception e) {
                if (!running.get()) return;
                logReceiveFailure(e);
                sleepQuietly();
            }
        }
    }

    /**
     * 수신 실패가 이어질 때(큐에 네트워크가 닿지 않는 경우) 로그를 솎아낸다.
     *
     * <p>5초마다 ERROR를 남기면 장애 한 번에 Sentry Issue가 수천 건 쌓이고, 로그 적재 자체가
     * 서버를 흔든다. 첫 실패와 이후 5분 간격만 ERROR로 올리고 나머지는 DEBUG로 떨어뜨린다.
     */
    private void logReceiveFailure(Exception e) {
        boolean firstOrPeriodic = receiveFailures % RECEIVE_ERROR_LOG_EVERY == 0;
        receiveFailures++;
        if (firstOrPeriodic) {
            log.error("응답 큐 수신 실패 (누적 {}회) — {}초 후 재시도",
                    receiveFailures, RECEIVE_ERROR_BACKOFF_MS / 1000, e);
        } else {
            log.debug("응답 큐 수신 실패 (누적 {}회)", receiveFailures, e);
        }
    }

    private List<Message> receiveOnce() {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(properties.getResponseQueueUrl())
                .waitTimeSeconds(properties.getPollWaitSeconds())
                .maxNumberOfMessages(properties.getPollMaxMessages())
                .build()).messages();
    }

    void handle(Message message) {
        EmotionAnalysisResponseMessage response;
        try {
            response = objectMapper.readValue(message.body(), EmotionAnalysisResponseMessage.class);
        } catch (Exception e) {
            discardPoison("응답 메시지 역직렬화 실패", message, e);
            return;
        }

        EmotionAnalysisResultApplier.ApplyResult result;
        try {
            result = resultApplier.apply(response);
        } catch (EmotionAnalysisContractException e) {
            discardPoison("계약을 벗어난 응답", message, e);
            return;
        } catch (Exception e) {
            // DB 장애 등 복구 가능한 실패 — 삭제하지 않아 Visibility Timeout 뒤 재수신된다.
            log.error("응답 반영 실패 (ACK 안 함, 재수신 대기) — requestId={}", response.requestId(), e);
            return;
        }

        switch (result.outcome()) {
            case SETTLED -> {
                log.info("소분류 감정 분석 반영 — requestId={}, voiceId={}, status={}",
                        response.requestId(), result.voiceId(), response.processingStatus());
                // 삭제 전에 발행한다. 삭제가 실패해 재수신되면 DUPLICATE로 걸러져 중복 발송되지 않는다.
                publishCompleted(response.requestId(), result.voiceId());
            }
            case DUPLICATE -> log.info("이미 마감된 요청의 중복 응답 — requestId={}", response.requestId());
            case UNKNOWN_REQUEST -> log.warn(
                    "원장에 없는 request_id — 폐기(ACK). requestId={}", response.requestId());
        }
        delete(message);
    }

    /**
     * 재수신해도 처리할 수 없는 메시지를 폐기한다.
     *
     * <p>응답 큐에 DLQ가 없어 ACK하지 않으면 보관 기간 내내 같은 메시지가 되돌아온다.
     * 폐기해도 원문은 S3 {@code response_key}에 남고, 원장은 PENDING이라 타임아웃 스윕이
     * 일기를 마감하므로 사용자 흐름은 끊기지 않는다. 조사용으로 본문을 남기되 길이를 제한한다
     * (응답 본문에는 전사가 들어오지 않고 판정 결과·오류만 들어온다).
     */
    private void discardPoison(String reason, Message message, Exception cause) {
        String body = message.body() == null ? "" : message.body();
        log.error("{} — 폐기(ACK). messageId={}, body={}{}",
                reason, message.messageId(),
                body.length() <= POISON_LOG_BODY_LIMIT ? body : body.substring(0, POISON_LOG_BODY_LIMIT),
                body.length() <= POISON_LOG_BODY_LIMIT ? "" : "...(truncated)", cause);
        delete(message);
    }

    /** 분석 완료 푸시·후속 처리 트리거. 실패해도 DB 반영은 이미 커밋됐으므로 삼킨다. */
    private void publishCompleted(String requestId, Long voiceId) {
        try {
            eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voiceId));
        } catch (Exception e) {
            log.warn("VoiceAnalysisCompletedEvent 발행 실패 (분석 결과는 반영됨) — requestId={}",
                    requestId, e);
        }
    }

    private void delete(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(properties.getResponseQueueUrl())
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            // 삭제 실패는 재수신으로 이어지지만 중복 처리는 원장이 막는다.
            log.warn("응답 메시지 삭제 실패 — messageId={}", message.messageId(), e);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RECEIVE_ERROR_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
