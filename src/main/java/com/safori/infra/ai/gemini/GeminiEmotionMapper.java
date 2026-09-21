package com.safori.infra.ai.gemini;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.domain.chatbot.model.VoiceEmotionDigest;
import com.safori.domain.chatbot.model.VoiceEmotionLabelView;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.ai.gemini.dto.GeminiEmotionScore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiEmotionMapper {

    // va_fusion의 EMOTION_VA 앵커값
    private static final Map<EmotionType, double[]> VA_ANCHOR = new EnumMap<>(EmotionType.class);

    static {
        VA_ANCHOR.put(EmotionType.HAPPY,    new double[]{+0.80, +0.60});
        VA_ANCHOR.put(EmotionType.SAD,      new double[]{-0.70, -0.40});
        VA_ANCHOR.put(EmotionType.NEUTRAL,  new double[]{ 0.00,  0.00});
        VA_ANCHOR.put(EmotionType.ANGRY,    new double[]{-0.70, +0.80});
        VA_ANCHOR.put(EmotionType.ANXIETY,  new double[]{-0.60, +0.70});
        VA_ANCHOR.put(EmotionType.SURPRISE, new double[]{ 0.00, +0.85});
    }

    public VoiceComposite toVoiceComposite(GeminiAnalysisResult result, Voice voice) {
        Map<EmotionType, Double> intensitySum = aggregateIntensity(result);
        Map<EmotionType, Integer> bps = toBps(intensitySum);

        EmotionType topEmotion = bps.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EmotionType.NEUTRAL);

        int[] va = computeVA(bps);

        return VoiceComposite.builder()
                .voice(voice)
                .happyBps(bps.getOrDefault(EmotionType.HAPPY, 0))
                .sadBps(bps.getOrDefault(EmotionType.SAD, 0))
                .neutralBps(bps.getOrDefault(EmotionType.NEUTRAL, 0))
                .angryBps(bps.getOrDefault(EmotionType.ANGRY, 0))
                .anxietyBps(bps.getOrDefault(EmotionType.ANXIETY, 0))
                .surpriseBps(bps.getOrDefault(EmotionType.SURPRISE, 0))
                .topEmotion(topEmotion)
                .topEmotionConfidenceBps(bps.getOrDefault(topEmotion, 0))
                .valenceX1000(va[0])
                .arousalX1000(va[1])
                .intensityX1000(va[2])
                .summary(result.summary())
                .title(clampTitle(result.title()))
                .build();
    }

    /**
     * 프롬프트는 15자 이내 제목을 요구하지만 Gemini가 이를 지키지 않는 경우가 있다.
     * 컬럼 길이를 넘기면 저장이 통째로 실패하므로 초과분은 잘라낸다.
     */
    private String clampTitle(String title) {
        if (title == null) return null;
        String trimmed = title.strip();
        if (trimmed.length() <= VoiceComposite.TITLE_MAX_LENGTH) return trimmed;
        return trimmed.substring(0, VoiceComposite.TITLE_MAX_LENGTH);
    }

    /**
     * 분석 결과에서 세부 감정 레이블을 voice 단위로 집계하여 반환.
     * 동일 label이 여러 세그먼트에 걸쳐 있으면 intensity를 합산.
     */
    public List<VoiceEmotionLabel> toEmotionLabels(GeminiAnalysisResult result, Voice voice) {
        // label → [category, intensitySum]
        Map<String, String> labelCategory = new LinkedHashMap<>();
        Map<String, Double> labelIntensity = new LinkedHashMap<>();

        if (result.segments() != null) {
            for (var segment : result.segments()) {
                if (segment.emotions() == null || segment.category() == null) continue;
                String category = segment.category();
                for (GeminiEmotionScore score : segment.emotions()) {
                    if (score == null || score.name() == null) continue;
                    labelCategory.putIfAbsent(score.name(), category);
                    labelIntensity.merge(score.name(), score.intensity(), Double::sum);
                }
            }
        }

        List<VoiceEmotionLabel> labels = new ArrayList<>();
        for (Map.Entry<String, Double> entry : labelIntensity.entrySet()) {
            String label = entry.getKey();
            int intensityX1000 = (int) Math.round(entry.getValue() * 1000);
            labels.add(VoiceEmotionLabel.builder()
                    .voice(voice)
                    .category(labelCategory.get(label))
                    .label(label)
                    .intensityX1000(intensityX1000)
                    .build());
        }
        return labels;
    }

    /** 챗봇 음성 리프레이밍용 요약: 대표감정 + bps + 상위 N개 라벨 (Voice 비의존). */
    public VoiceEmotionDigest toVoiceEmotionDigest(GeminiAnalysisResult result, int labelLimit) {
        Map<EmotionType, Integer> bps = toBps(aggregateIntensity(result));
        EmotionType topEmotion = bps.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EmotionType.NEUTRAL);

        Map<String, Double> labelIntensity = new LinkedHashMap<>();
        if (result.segments() != null) {
            for (var segment : result.segments()) {
                if (segment.emotions() == null) continue;
                for (GeminiEmotionScore score : segment.emotions()) {
                    if (score == null || score.name() == null) continue;
                    labelIntensity.merge(score.name(), score.intensity(), Double::sum);
                }
            }
        }

        List<VoiceEmotionLabelView> labels = labelIntensity.entrySet().stream()
                .map(e -> new VoiceEmotionLabelView(e.getKey(), (int) Math.round(e.getValue() * 1000)))
                .sorted(Comparator.comparingInt(VoiceEmotionLabelView::intensityX1000).reversed())
                .limit(Math.max(0, labelLimit))
                .toList();

        return new VoiceEmotionDigest(topEmotion, bps.getOrDefault(topEmotion, 0), labels);
    }

    private Map<EmotionType, Double> aggregateIntensity(GeminiAnalysisResult result) {
        Map<EmotionType, Double> sum = new EnumMap<>(EmotionType.class);
        if (result.segments() == null) return sum;
        for (var segment : result.segments()) {
            if (segment.emotions() == null || segment.category() == null) continue;
            String category = segment.category();
            for (GeminiEmotionScore score : segment.emotions()) {
                if (score == null || score.name() == null) continue;
                GeminiEmotionMapping.resolve(score.name(), category)
                        .ifPresent(type -> sum.merge(type, score.intensity(), Double::sum));
            }
        }
        return sum;
    }

    private Map<EmotionType, Integer> toBps(Map<EmotionType, Double> intensitySum) {
        double total = intensitySum.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            Map<EmotionType, Integer> fallback = new EnumMap<>(EmotionType.class);
            for (EmotionType t : EmotionType.values()) fallback.put(t, 0);
            fallback.put(EmotionType.NEUTRAL, 10000);
            return fallback;
        }

        Map<EmotionType, Integer> bps = new EnumMap<>(EmotionType.class);
        int assigned = 0;
        EmotionType maxType = EmotionType.NEUTRAL;
        int maxVal = -1;

        for (EmotionType type : EmotionType.values()) {
            double intensity = intensitySum.getOrDefault(type, 0.0);
            int val = (int) Math.round(intensity / total * 10000);
            bps.put(type, val);
            assigned += val;
            if (val > maxVal) { maxVal = val; maxType = type; }
        }

        // 반올림 오차 보정 (합계 = 10000 보증)
        bps.put(maxType, bps.get(maxType) + (10000 - assigned));
        return bps;
    }

    private int[] computeVA(Map<EmotionType, Integer> bps) {
        double v = 0, a = 0;
        for (EmotionType type : EmotionType.values()) {
            double weight = bps.getOrDefault(type, 0) / 10000.0;
            double[] anchor = VA_ANCHOR.get(type);
            v += weight * anchor[0];
            a += weight * anchor[1];
        }
        double intensity = Math.sqrt(v * v + a * a);
        return new int[]{
                (int) Math.round(v * 1000),
                (int) Math.round(a * 1000),
                (int) Math.round(intensity * 1000)
        };
    }
}
