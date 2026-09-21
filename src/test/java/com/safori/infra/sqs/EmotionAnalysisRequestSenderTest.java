package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.emotion.adaptor.EmotionAnalysisRequestAdaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.emotion.entity.EmotionAnalysisStatus;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.ai.gemini.dto.GeminiEmotionScore;
import com.safori.infra.ai.gemini.dto.GeminiSegment;
import com.safori.infra.sqs.config.EmotionAnalysisSqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisRequestSenderTest {

    private static final String QUEUE_URL =
            "https://sqs.ap-northeast-2.amazonaws.com/000000000000/RequestEmotionAnalysis";

    @Mock SqsClient sqsClient;
    @Mock EmotionAnalysisRequestAdaptor requestAdaptor;

    // 운영에서는 Boot가 등록한 ObjectMapper(JavaTimeModule 포함)가 주입된다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private EmotionAnalysisSqsProperties properties;
    private Voice voice;

    @BeforeEach
    void setUp() {
        properties = new EmotionAnalysisSqsProperties();
        properties.setRequestQueueUrl(QUEUE_URL);
        voice = Voice.builder().id(10L).user(User.builder().id(7L).build()).build();
    }

    private EmotionAnalysisRequestSender sender(Optional<SqsClient> client) {
        return new EmotionAnalysisRequestSender(
                client, properties, requestAdaptor, new EmotionAnalysisPayloadMapper(), objectMapper);
    }

    private GeminiAnalysisResult geminiResult() {
        return new GeminiAnalysisResult(
                "오늘 시험 결과가 잘 나와서 기분이 좋아요",
                "예상보다 좋은 결과에 기쁨을 느끼고 계시네요",
                "예상 밖의 좋은 결과",
                List.of(
                        new GeminiSegment("00:00", "오늘 시험 결과가 잘 나왔어요", "happy",
                                List.of(new GeminiEmotionScore("joy", 0.75),
                                        new GeminiEmotionScore("pride", 0.30)),
                                "말끝이 올라간다"),
                        new GeminiSegment("00:06", "기분이 좋아요", "happy",
                                List.of(new GeminiEmotionScore("joy", 0.60),
                                        new GeminiEmotionScore("pride", 0.52)),
                                "웃음 섞인 음색")),
                7.5);
    }

    private void stubLedgerSave() {
        when(requestAdaptor.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private JsonNode capturedBody() throws Exception {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.captor();
        verify(sqsClient).sendMessage(captor.capture());
        assertThat(captor.getValue().queueUrl()).isEqualTo(QUEUE_URL);
        return objectMapper.readTree(captor.getValue().messageBody());
    }

    @Test
    @DisplayName("전송 본문 — 계약 필드(snake_case) + 봉투 식별자")
    void sendsContractShapedBody() throws Exception {
        stubLedgerSave();

        assertThat(sender(Optional.of(sqsClient)).handOff(voice, geminiResult(), EmotionType.HAPPY))
                .isTrue();

        JsonNode body = capturedBody();
        assertThat(body.get("request_id").asText()).isNotBlank();
        assertThat(body.get("user_id").asLong()).isEqualTo(7L);
        assertThat(body.get("clip_id").asLong()).isEqualTo(10L);

        JsonNode gemini = body.get("gemini_result");
        assertThat(gemini.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("transcript", "summary", "prosody", "major", "detected");
        assertThat(gemini.get("transcript").asText()).startsWith("오늘 시험 결과");
        assertThat(gemini.get("prosody").asText()).isEqualTo("말끝이 올라간다 웃음 섞인 음색");
    }

    @Test
    @DisplayName("major 없으면 Lambda가 INVALID_MAJOR로 거절한다 — 공식 대분류 1개를 반드시 넣는다")
    void alwaysSendsOfficialMajor() throws Exception {
        stubLedgerSave();

        sender(Optional.of(sqsClient)).handOff(voice, geminiResult(), EmotionType.ANXIETY);

        JsonNode major = capturedBody().get("gemini_result").get("major");
        assertThat(major.isArray()).isTrue();
        assertThat(major).hasSize(1);
        assertThat(major.get(0).asText()).isEqualTo("ANXIETY");
    }

    @Test
    @DisplayName("detected — 대문자 code, 세그먼트 최대 강도, confidence 내림차순")
    void detectedUsesUppercaseCodeAndMaxIntensity() throws Exception {
        stubLedgerSave();

        sender(Optional.of(sqsClient)).handOff(voice, geminiResult(), EmotionType.HAPPY);

        JsonNode detected = capturedBody().get("gemini_result").get("detected");
        assertThat(detected).hasSize(2);
        assertThat(detected.get(0).get("code").asText()).isEqualTo("JOY");
        assertThat(detected.get(0).get("confidence").asDouble()).isEqualTo(0.75);
        assertThat(detected.get(1).get("code").asText()).isEqualTo("PRIDE");
        assertThat(detected.get(1).get("confidence").asDouble()).isEqualTo(0.52);
    }

    @Test
    @DisplayName("requested_at은 초 단위 — Wrapper의 Python fromisoformat이 못 읽는 자릿수를 피한다")
    void requestedAtHasNoFractionalSeconds() throws Exception {
        stubLedgerSave();

        sender(Optional.of(sqsClient)).handOff(voice, geminiResult(), EmotionType.HAPPY);

        String requestedAt = capturedBody().get("requested_at").asText();
        // 예: 2026-08-13T23:10:00+09:00
        assertThat(requestedAt)
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([+-]\\d{2}:\\d{2}|Z)");
    }

    @Test
    @DisplayName("전송 실패 — SEND_FAILED로 기록하고 false (호출측이 Gemini 소분류로 마감)")
    void marksSendFailed() {
        stubLedgerSave();
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("boom").build());

        assertThat(sender(Optional.of(sqsClient)).handOff(voice, geminiResult(), EmotionType.HAPPY))
                .isFalse();

        ArgumentCaptor<EmotionAnalysisRequest> captor = ArgumentCaptor.captor();
        verify(requestAdaptor, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus())
                .isEqualTo(EmotionAnalysisStatus.SEND_FAILED);
    }

    @Test
    @DisplayName("큐 미설정 — 원장도 남기지 않고 즉시 false")
    void disabledWhenNoClient() {
        assertThat(sender(Optional.empty()).handOff(voice, geminiResult(), EmotionType.HAPPY))
                .isFalse();
        verify(requestAdaptor, never()).save(any());
    }
}
