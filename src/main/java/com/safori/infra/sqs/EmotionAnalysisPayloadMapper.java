package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safori.common.consts.EmotionLabelStaticValues;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.ai.gemini.dto.GeminiEmotionScore;
import com.safori.infra.ai.gemini.dto.GeminiSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini 1차 분석 결과 → 요청 계약의 {@code gemini_result} 페이로드.
 *
 * <p>분석 Lambda는 이 형상을 강제한다. {@code major}가 공식 대분류를 하나도 담고 있지 않으면
 * {@code INVALID_MAJOR} 4xx로 거절한다. Gemini 원본(segments·title·stability_score)을 그대로
 * 넘기면 통과하지 못하므로 계약 필드만 추려 보낸다.
 */
@Slf4j
@Component
public class EmotionAnalysisPayloadMapper {

    public ObjectNode toGeminiResultPayload(GeminiAnalysisResult result, EmotionType major) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();

        if (result.transcript() != null) payload.put("transcript", result.transcript());
        if (result.summary() != null) payload.put("summary", result.summary());
        payload.put("prosody", joinProsody(result));

        // 대분류는 Gemini 판정을 그대로 넘긴다 — 소분류 판정이 바꾸지 않는 전제다.
        ArrayNode majors = payload.putArray("major");
        majors.add((major != null ? major : EmotionType.NEUTRAL).name());

        ArrayNode detected = payload.putArray("detected");
        for (Map.Entry<String, Double> entry : detectedConfidence(result).entrySet()) {
            ObjectNode item = detected.addObject();
            item.put("code", entry.getKey().toUpperCase(Locale.ROOT));
            item.put("confidence", entry.getValue());
        }
        return payload;
    }

    /** 세그먼트별 관찰을 한 문장열로 합친다. 중복 문장은 한 번만. */
    private String joinProsody(GeminiAnalysisResult result) {
        if (result.segments() == null) return "";
        return result.segments().stream()
                .map(GeminiSegment::prosodyNotes)
                .filter(note -> note != null && !note.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    /**
     * label → confidence(0~1). 같은 label이 여러 세그먼트에 나오면 <b>가장 강했던 값</b>을 쓴다.
     * 합산하면 1을 넘어 계약을 벗어나고, 평균을 내면 순간적으로 강했던 감정이 묻힌다.
     *
     * <p>48개 표준 어휘 밖 이름은 보내지 않는다 — Lambda가 공식 코드만 인정한다.
     */
    private Map<String, Double> detectedConfidence(GeminiAnalysisResult result) {
        Map<String, Double> byLabel = new LinkedHashMap<>();
        if (result.segments() == null) return byLabel;

        List<String> dropped = new ArrayList<>();
        for (GeminiSegment segment : result.segments()) {
            if (segment.emotions() == null) continue;
            for (GeminiEmotionScore score : segment.emotions()) {
                if (score == null || score.name() == null) continue;
                String label = EmotionLabelStaticValues.normalizeLabel(score.name());
                if (!EmotionLabelStaticValues.isKnownLabel(label)) {
                    dropped.add(label);
                    continue;
                }
                byLabel.merge(label, clamp01(score.intensity()), Math::max);
            }
        }
        if (!dropped.isEmpty()) {
            log.warn("Gemini가 표준 어휘 밖 감정을 반환 — 요청에서 제외. labels={}", dropped);
        }

        return byLabel.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private double clamp01(double intensity) {
        return Math.max(0.0, Math.min(1.0, intensity));
    }
}
