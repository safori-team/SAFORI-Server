package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionAnalysisLabelMapperTest {

    private final EmotionAnalysisLabelMapper mapper = new EmotionAnalysisLabelMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Voice voice = Voice.builder().id(1L).build();

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }

    @Test
    @DisplayName("대문자 code를 소문자 label로 정규화하고 category를 채운다")
    void normalizesUppercaseCode() throws Exception {
        List<VoiceEmotionLabel> labels = mapper.toEmotionLabels(json("""
                {"minor_categories":[
                  {"code":"JOY","confidence":0.9},
                  {"code":"SATISFACTION","confidence":0.63}
                ]}
                """), voice);

        assertThat(labels).extracting(VoiceEmotionLabel::getLabel)
                .containsExactly("joy", "satisfaction");
        assertThat(labels).extracting(VoiceEmotionLabel::getCategory)
                .containsExactly("happy", "happy");
        assertThat(labels).extracting(VoiceEmotionLabel::getIntensityX1000)
                .containsExactly(900, 630);
    }

    @Test
    @DisplayName("48개 표준 어휘 밖 code는 버린다")
    void dropsUnknownCode() throws Exception {
        List<VoiceEmotionLabel> labels = mapper.toEmotionLabels(json("""
                {"minor_categories":[
                  {"code":"MADE_UP_EMOTION","confidence":0.9},
                  {"code":"LONELINESS","confidence":0.4}
                ]}
                """), voice);

        assertThat(labels).extracting(VoiceEmotionLabel::getLabel).containsExactly("loneliness");
        assertThat(labels.get(0).getCategory()).isEqualTo("sad");
    }

    @Test
    @DisplayName("같은 label이 두 번 오면 먼저 온 것만 남긴다 — uq_vel_voice_label 위반 방지")
    void dedupesLabel() throws Exception {
        List<VoiceEmotionLabel> labels = mapper.toEmotionLabels(json("""
                {"minor_categories":[
                  {"code":"ANGER","confidence":0.8},
                  {"code":"anger","confidence":0.2}
                ]}
                """), voice);

        assertThat(labels).hasSize(1);
        assertThat(labels.get(0).getIntensityX1000()).isEqualTo(800);
    }

    @Test
    @DisplayName("confidence는 0~1로 clamp — 범위 밖 값이 와도 intensity가 넘치지 않는다")
    void clampsConfidence() throws Exception {
        List<VoiceEmotionLabel> labels = mapper.toEmotionLabels(json("""
                {"minor_categories":[
                  {"code":"FEAR","confidence":1.7},
                  {"code":"HORROR","confidence":-0.3}
                ]}
                """), voice);

        assertThat(labels).extracting(VoiceEmotionLabel::getIntensityX1000)
                .containsExactly(1000, 0);
    }

    @Test
    @DisplayName("minor_categories가 없거나 배열이 아니면 빈 리스트 — 기존 Gemini 소분류를 유지시킨다")
    void emptyWhenMissing() throws Exception {
        assertThat(mapper.toEmotionLabels(json("""
                {"status":400,"error":{"code":"INVALID_REQUEST"}}
                """), voice)).isEmpty();
        assertThat(mapper.toEmotionLabels(json("""
                {"minor_categories":"none"}
                """), voice)).isEmpty();
        assertThat(mapper.toEmotionLabels(null, voice)).isEmpty();
    }
}
