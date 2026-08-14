package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.JsonNode;
import com.safori.common.consts.EmotionLabelStaticValues;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 분석 Lambda의 {@code analysis_result.minor_categories} → {@link VoiceEmotionLabel} 변환.
 *
 * <p>code는 대문자로 오고 DB·한글 매핑은 소문자 키를 쓰므로 정규화한다. 48개 표준 어휘 밖의
 * code는 버린다 — 저장해봐야 한글 표시명이 없어 프론트에 영문이 그대로 노출된다.
 *
 * <p>category는 응답에 없으므로 label → 대분류 정적 매핑으로 채운다. 대분류 자체
 * ({@code voice_composite})는 이 경로에서 건드리지 않는다.
 */
@Slf4j
@Component
public class EmotionAnalysisLabelMapper {

    private static final String MINOR_CATEGORIES = "minor_categories";

    /**
     * @return 검증을 통과한 소분류 라벨. 하나도 없으면 빈 리스트 (호출측이 기존 Gemini 소분류를
     *         유지한다)
     */
    public List<VoiceEmotionLabel> toEmotionLabels(JsonNode analysisResult, Voice voice) {
        if (analysisResult == null) return List.of();

        JsonNode minors = analysisResult.path(MINOR_CATEGORIES);
        if (!minors.isArray()) return List.of();

        List<VoiceEmotionLabel> labels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int dropped = 0;

        for (JsonNode minor : minors) {
            String label = EmotionLabelStaticValues.normalizeLabel(minor.path("code").asText(null));
            if (label == null || label.isBlank()) continue;
            if (!EmotionLabelStaticValues.isKnownLabel(label)) {
                dropped++;
                continue;
            }
            // uq_vel_voice_label(voice_id, label) — 중복 code가 오면 먼저 온 쪽만 남긴다.
            if (!seen.add(label)) continue;

            labels.add(VoiceEmotionLabel.builder()
                    .voice(voice)
                    .category(EmotionLabelStaticValues.categoryOf(label))
                    .label(label)
                    .intensityX1000(toIntensityX1000(minor.path("confidence").asDouble(0.0)))
                    .build());
        }

        if (dropped > 0) {
            log.warn("소분류 응답에 표준 어휘 밖 code {}건 — 폐기. voiceId={}", dropped, voice.getId());
        }
        return labels;
    }

    /**
     * confidence(0~1)를 기존 스키마의 {@code intensity_x1000}에 맞춘다.
     *
     * <p>Gemini 소분류는 세그먼트 intensity 합산이라 1000을 넘을 수 있는 반면 이 값은 0~1000에
     * 갇힌다. 같은 일기 안에서는 한쪽 출처로만 채워지므로 버블 크기 비교는 일기 단위로 일관된다.
     */
    private int toIntensityX1000(double confidence) {
        double clamped = Math.max(0.0, Math.min(1.0, confidence));
        return (int) Math.round(clamped * 1000);
    }
}
