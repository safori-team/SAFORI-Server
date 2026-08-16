package com.safori.infra.ai.gemini.config;

import com.google.genai.types.HarmCategory;
import com.google.genai.types.SafetySetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiSafetyPropertiesTest {

    private GeminiSafetyProperties props;

    @BeforeEach
    void setUp() {
        props = new GeminiSafetyProperties();
    }

    private static Map<String, String> byCategory(List<SafetySetting> settings) {
        return settings.stream().collect(Collectors.toMap(
                s -> s.category().map(Object::toString).orElse("?"),
                s -> s.threshold().map(Object::toString).orElse("?"),
                (a, b) -> a));
    }

    @Test
    @DisplayName("기본값은 네 카테고리를 모두 명시한다 — 미지정 시 모델 기본값이 사실상 차단하지 않기 때문")
    void defaultsCoverFourCategories() {
        Map<String, String> settings = byCategory(props.toSafetySettings());

        assertThat(settings).hasSize(4);
        assertThat(settings)
                .containsEntry(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT.toString(),
                        "BLOCK_MEDIUM_AND_ABOVE")
                .containsEntry(HarmCategory.Known.HARM_CATEGORY_HARASSMENT.toString(),
                        "BLOCK_MEDIUM_AND_ABOVE")
                .containsEntry(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH.toString(),
                        "BLOCK_ONLY_HIGH")
                .containsEntry(HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT.toString(),
                        "BLOCK_ONLY_HIGH");
    }

    @Test
    @DisplayName("enabled=false면 빈 목록 — 호출자가 필드 자체를 빼고 모델 기본값에 맡긴다")
    void disabledYieldsEmptyList() {
        props.setEnabled(false);

        assertThat(props.toSafetySettings()).isEmpty();
    }

    @Test
    @DisplayName("임계값을 비우면 그 카테고리만 모델 기본값에 맡긴다")
    void blankThresholdSkipsCategory() {
        props.setHateSpeech("");
        props.setSexuallyExplicit(null);

        assertThat(byCategory(props.toSafetySettings())).hasSize(2)
                .containsKey(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT.toString());
    }

    @Test
    @DisplayName("설정값의 공백·소문자를 정규화해 SDK에 넘긴다")
    void normalizesThresholdValue() {
        props.setDangerousContent("  block_low_and_above  ");

        assertThat(byCategory(props.toSafetySettings()))
                .containsEntry(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT.toString(),
                        "BLOCK_LOW_AND_ABOVE");
    }

    @Test
    @DisplayName("카테고리별로 임계값을 따로 조절할 수 있다 — 운영에서 오탐/미탐을 보며 조인다")
    void thresholdsAreIndependentlyTunable() {
        props.setDangerousContent("BLOCK_LOW_AND_ABOVE");

        Map<String, String> settings = byCategory(props.toSafetySettings());
        assertThat(settings).containsEntry(
                HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT.toString(), "BLOCK_LOW_AND_ABOVE");
        assertThat(settings).containsEntry(
                HarmCategory.Known.HARM_CATEGORY_HARASSMENT.toString(), "BLOCK_MEDIUM_AND_ABOVE");
    }
}
