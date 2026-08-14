package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.infra.sqs.config.EmotionAnalysisSqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ACK 정책 고정. 응답 큐에 DLQ가 없으므로 "삭제하지 않는다"는 곧 보관 기간 내내 재수신을
 * 뜻한다 — 복구 가능한 실패에서만 남겨야 한다.
 */
@ExtendWith(MockitoExtension.class)
class EmotionAnalysisResponsePollerTest {

    private static final String VALID_BODY = """
            {"request_id":"r-1","user_id":10,"clip_id":156836,"processing_status":"COMPLETED",
             "analysis_result":{"minor_categories":[{"code":"JOY","confidence":0.9}]},
             "request_key":"k/request.json","response_key":"k/response.json",
             "completed_at":"2026-08-13T14:15:47.184991+00:00"}
            """;

    @Mock SqsClient sqsClient;
    @Mock EmotionAnalysisResultApplier resultApplier;
    @Mock ApplicationEventPublisher eventPublisher;

    private EmotionAnalysisResponsePoller poller;

    @BeforeEach
    void setUp() {
        EmotionAnalysisSqsProperties properties = new EmotionAnalysisSqsProperties();
        properties.setResponseQueueUrl(
                "https://sqs.ap-northeast-2.amazonaws.com/000000000000/ResponseEmotionAnalysis");
        poller = new EmotionAnalysisResponsePoller(
                sqsClient, properties, resultApplier,
                new ObjectMapper().findAndRegisterModules(), eventPublisher);
    }

    private Message message(String body) {
        return Message.builder().messageId("m-1").receiptHandle("rh-1").body(body).build();
    }

    @Test
    @DisplayName("정상 반영 — 완료 이벤트 발행 후 삭제")
    void settledPublishesAndDeletes() {
        when(resultApplier.apply(any())).thenReturn(new EmotionAnalysisResultApplier.ApplyResult(
                EmotionAnalysisResultApplier.Outcome.SETTLED, 10L));

        poller.handle(message(VALID_BODY));

        verify(eventPublisher).publishEvent(new VoiceAnalysisCompletedEvent(10L));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("중복 응답 — 이벤트 없이 삭제만")
    void duplicateDeletesWithoutEvent() {
        when(resultApplier.apply(any())).thenReturn(new EmotionAnalysisResultApplier.ApplyResult(
                EmotionAnalysisResultApplier.Outcome.DUPLICATE, 10L));

        poller.handle(message(VALID_BODY));

        verify(eventPublisher, never()).publishEvent(any());
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("역직렬화 불가 — 폐기(ACK). DLQ가 없어 남기면 보관 기간 내내 되돌아온다")
    void poisonMessageIsDiscarded() {
        poller.handle(message("{ this is not json"));

        verify(resultApplier, never()).apply(any());
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("계약 밖 processing_status — 폐기(ACK). 원문은 S3에 남아 있다")
    void contractViolationIsDiscarded() {
        when(resultApplier.apply(any()))
                .thenThrow(new EmotionAnalysisContractException("Unknown processing_status: RETRYING"));

        poller.handle(message(VALID_BODY));

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("DB 장애 — 삭제하지 않는다. 복구되면 재수신으로 처리된다")
    void transientFailureKeepsMessage() {
        when(resultApplier.apply(any())).thenThrow(new RuntimeException("db down"));

        poller.handle(message(VALID_BODY));

        verify(eventPublisher, never()).publishEvent(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
