package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.emotion.adaptor.EmotionAnalysisRequestAdaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.emotion.entity.EmotionAnalysisStatus;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.infra.sqs.dto.EmotionAnalysisResponseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 응답 큐에서 받은 메시지 원문으로 계약을 고정한다.
 *
 * <p>Wrapper가 붙이는 봉투와 분석 Lambda의 결과 형상이 바뀌면 여기서 먼저 깨진다.
 * (2026-08-13 콘솔 연동 테스트에서 수신한 COMPLETED 응답 그대로)
 */
@ExtendWith(MockitoExtension.class)
class EmotionAnalysisResponseContractTest {

    private static final String REAL_RESPONSE_BODY = """
            {"request_id": "console-test-20260813-02", "user_id": 10, "clip_id": 156836,
             "processing_status": "COMPLETED",
             "analysis_result": {"request_id": "console-test-20260813-02",
               "minor_categories": [{"code": "JOY", "confidence": 0.9},
                 {"code": "SATISFACTION", "confidence": 0.9},
                 {"code": "PRIDE", "confidence": 0.8},
                 {"code": "RELIEF", "confidence": 0.7}],
               "meta": {"engine": "lightrag_native_query", "mode": "hybrid",
                 "model": "gemini-2.5-flash"}},
             "request_key": "analysis/users/10/2026/08/13/console-test-20260813-02/request.json",
             "response_key": "analysis/users/10/2026/08/13/console-test-20260813-02/response.json",
             "completed_at": "2026-08-13T14:15:47.184991+00:00"}
            """;

    @Mock EmotionAnalysisRequestAdaptor requestAdaptor;
    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @Mock VoiceAdaptor voiceAdaptor;

    // 운영에서는 Boot가 등록한 ObjectMapper(JavaTimeModule 포함)가 주입된다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private EmotionAnalysisResultApplier applier;
    private Voice voice;

    @BeforeEach
    void setUp() {
        applier = new EmotionAnalysisResultApplier(
                requestAdaptor, voiceEmotionLabelAdaptor, voiceAdaptor,
                new EmotionAnalysisLabelMapper());
        voice = Voice.builder().id(10L).build();
        voice.markAnalysisProcessing();
    }

    private EmotionAnalysisResponseMessage parse() throws Exception {
        return objectMapper.readValue(REAL_RESPONSE_BODY, EmotionAnalysisResponseMessage.class);
    }

    @Test
    @DisplayName("실제 응답 원문 역직렬화 — 봉투 필드가 계약대로 매핑된다")
    void deserializesRealResponse() throws Exception {
        EmotionAnalysisResponseMessage message = parse();

        assertThat(message.requestId()).isEqualTo("console-test-20260813-02");
        assertThat(message.userId()).isEqualTo(10L);
        assertThat(message.clipId()).isEqualTo(156836L);
        assertThat(message.processingStatus()).isEqualTo("COMPLETED");
        assertThat(message.requestKey()).endsWith("/request.json");
        assertThat(message.responseKey()).endsWith("/response.json");
        assertThat(message.completedAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-13T14:15:47.184991+00:00"));
        // 계약에 없는 meta가 붙어도 깨지지 않고, 원문은 그대로 보관된다.
        assertThat(message.analysisResult().path("meta").path("engine").asText())
                .isEqualTo("lightrag_native_query");
    }

    @Test
    @DisplayName("실제 응답 반영 — 소분류 4건이 순서·강도·대분류까지 그대로 저장된다")
    void appliesRealResponse() throws Exception {
        EmotionAnalysisRequest request = EmotionAnalysisRequest.builder()
                .requestId("console-test-20260813-02")
                .voice(voice)
                .status(EmotionAnalysisStatus.PENDING)
                .build();
        when(requestAdaptor.queryByRequestIdForUpdate("console-test-20260813-02"))
                .thenReturn(Optional.of(request));

        var result = applier.apply(parse());

        assertThat(result.outcome()).isEqualTo(EmotionAnalysisResultApplier.Outcome.SETTLED);
        assertThat(request.getStatus()).isEqualTo(EmotionAnalysisStatus.COMPLETED);
        assertThat(voice.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(
                OffsetDateTime.parse("2026-08-13T14:15:47.184991+00:00")
                        .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());

        ArgumentCaptor<List<VoiceEmotionLabel>> captor = ArgumentCaptor.captor();
        verify(voiceEmotionLabelAdaptor).replaceByVoiceId(eq(10L), captor.capture());
        assertThat(captor.getValue()).extracting(
                        VoiceEmotionLabel::getLabel,
                        VoiceEmotionLabel::getCategory,
                        VoiceEmotionLabel::getIntensityX1000)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("joy", "happy", 900),
                        org.assertj.core.groups.Tuple.tuple("satisfaction", "happy", 900),
                        org.assertj.core.groups.Tuple.tuple("pride", "happy", 800),
                        org.assertj.core.groups.Tuple.tuple("relief", "happy", 700));
    }
}
