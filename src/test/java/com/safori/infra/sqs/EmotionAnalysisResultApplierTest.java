package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisResultApplierTest {

    private static final String REQUEST_ID = "1f0c2a2e-0000-0000-0000-000000000001";

    @Mock EmotionAnalysisRequestAdaptor requestAdaptor;
    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @Mock VoiceAdaptor voiceAdaptor;

    private EmotionAnalysisResultApplier applier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Voice voice;

    @BeforeEach
    void setUp() {
        applier = new EmotionAnalysisResultApplier(
                requestAdaptor, voiceEmotionLabelAdaptor, voiceAdaptor,
                new EmotionAnalysisLabelMapper());
        voice = Voice.builder().id(10L).build();
        voice.markAnalysisProcessing();
    }

    private EmotionAnalysisRequest pendingRequest() {
        return EmotionAnalysisRequest.builder()
                .requestId(REQUEST_ID)
                .voice(voice)
                .status(EmotionAnalysisStatus.PENDING)
                .build();
    }

    private EmotionAnalysisResponseMessage response(String status, String analysisResult) throws Exception {
        JsonNode result = analysisResult == null ? null : objectMapper.readTree(analysisResult);
        return new EmotionAnalysisResponseMessage(
                REQUEST_ID, 1L, 10L, status, result,
                "analysis/.../request.json", "analysis/.../response.json",
                OffsetDateTime.parse("2026-08-11T00:00:20Z"));
    }

    @Test
    @DisplayName("COMPLETED — 소분류 라벨을 교체하고 일기를 분석 완료로 마감한다")
    void completed() throws Exception {
        EmotionAnalysisRequest request = pendingRequest();
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        var result = applier.apply(response("COMPLETED", """
                {"minor_categories":[{"code":"JOY","confidence":0.9}]}
                """));

        assertThat(result.outcome()).isEqualTo(EmotionAnalysisResultApplier.Outcome.SETTLED);
        assertThat(result.voiceId()).isEqualTo(10L);
        assertThat(request.getStatus()).isEqualTo(EmotionAnalysisStatus.COMPLETED);
        assertThat(voice.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);

        ArgumentCaptor<List<VoiceEmotionLabel>> captor = ArgumentCaptor.captor();
        verify(voiceEmotionLabelAdaptor).replaceByVoiceId(eq(10L), captor.capture());
        assertThat(captor.getValue()).extracting(VoiceEmotionLabel::getLabel).containsExactly("joy");
    }

    @Test
    @DisplayName("FAILED — 오류 본문을 보관하되 Gemini 소분류는 그대로 두고 마감한다")
    void failed() throws Exception {
        EmotionAnalysisRequest request = pendingRequest();
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        var result = applier.apply(response("FAILED", """
                {"status":400,"error":{"code":"INVALID_REQUEST","message":"..."}}
                """));

        assertThat(result.outcome()).isEqualTo(EmotionAnalysisResultApplier.Outcome.SETTLED);
        assertThat(request.getStatus()).isEqualTo(EmotionAnalysisStatus.FAILED);
        assertThat(request.getAnalysisResult().path("status").asInt()).isEqualTo(400);
        assertThat(voice.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
        verify(voiceEmotionLabelAdaptor, never()).replaceByVoiceId(anyLong(), any());
    }

    @Test
    @DisplayName("이미 마감된 요청에 도착한 중복 응답 — DB를 손대지 않고 DUPLICATE")
    void duplicate() throws Exception {
        EmotionAnalysisRequest request = pendingRequest();
        request.complete(null, null, null, null);
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        var result = applier.apply(response("COMPLETED", """
                {"minor_categories":[{"code":"JOY","confidence":0.9}]}
                """));

        assertThat(result.outcome()).isEqualTo(EmotionAnalysisResultApplier.Outcome.DUPLICATE);
        verify(voiceEmotionLabelAdaptor, never()).replaceByVoiceId(anyLong(), any());
        verify(voiceAdaptor, never()).save(any());
    }

    @Test
    @DisplayName("원장에 없는 request_id — UNKNOWN_REQUEST (폴러가 폐기 ACK)")
    void unknownRequest() throws Exception {
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());

        var result = applier.apply(response("COMPLETED", "{}"));

        assertThat(result.outcome()).isEqualTo(EmotionAnalysisResultApplier.Outcome.UNKNOWN_REQUEST);
        assertThat(result.voiceId()).isNull();
        verify(voiceAdaptor, never()).save(any());
    }

    @Test
    @DisplayName("계약에 없는 processing_status — 복구 불가 예외로 구분 (폴러가 폐기)")
    void unknownStatus() throws Exception {
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> applier.apply(response("RETRYING", "{}")))
                .isInstanceOf(EmotionAnalysisContractException.class)
                .hasMessageContaining("RETRYING");
        verify(voiceAdaptor, never()).save(any());
    }

    @Test
    @DisplayName("유효한 소분류가 하나도 없으면 라벨을 지우지 않는다 — 빈 세부감정 방지")
    void keepsExistingLabelsWhenNoValidMinor() throws Exception {
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(pendingRequest()));

        applier.apply(response("COMPLETED", """
                {"minor_categories":[{"code":"NOT_IN_VOCABULARY","confidence":0.9}]}
                """));

        verify(voiceEmotionLabelAdaptor, never()).replaceByVoiceId(anyLong(), any());
        assertThat(voice.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("타임아웃 마감 — PENDING만 마감하고, 이미 최종 상태면 아무 것도 하지 않는다")
    void timeOut() {
        EmotionAnalysisRequest request = pendingRequest();
        when(requestAdaptor.queryByRequestIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThat(applier.timeOut(REQUEST_ID)).contains(10L);
        assertThat(request.getStatus()).isEqualTo(EmotionAnalysisStatus.TIMEOUT);
        assertThat(voice.getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);

        assertThat(applier.timeOut(REQUEST_ID)).isEmpty();
    }
}
