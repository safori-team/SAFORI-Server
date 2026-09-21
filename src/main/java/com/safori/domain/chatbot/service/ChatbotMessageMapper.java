package com.safori.domain.chatbot.service;

import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.VoiceEmotionLabelView;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 도란이 채팅 도메인의 변환 로직을 모은 매퍼.
 *
 * <ul>
 *   <li>{@link ChatbotReply} ↔ {@link JsonNode} (DB 저장용 bot_response)</li>
 *   <li>{@link VoiceComposite} + {@link VoiceEmotionLabel} → 프롬프트용 감정 컨텍스트 문자열</li>
 *   <li>이전 메시지 → HistoryTurn 변환</li>
 *   <li>JSON에서 봇 메시지 미리보기·감정·왜곡 라벨 추출</li>
 * </ul>
 *
 * <p>3개 UseCase + 1개 트리거에서 중복되던 헬퍼를 통합한 결과물이다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatbotMessageMapper {

    private final ObjectMapper objectMapper;

    // -- ChatbotReply ↔ bot_response JSON -----------------------------------

    /** {@link ChatbotReply} → MySQL JSON 컬럼에 저장할 {@link JsonNode}. */
    public JsonNode toBotResponseJson(ChatbotReply r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("empathy", r.empathy());
        m.put("detected_distortion", r.detectedDistortion());
        m.put("analysis", r.analysis());
        m.put("socratic_question", r.socraticQuestion());
        m.put("alternative_thought", r.alternativeThought());
        m.put("emotion", r.topEmotion());
        return objectMapper.valueToTree(m);
    }

    // -- 음성 감정 컨텍스트 -------------------------------------------------

    /**
     * VoiceComposite + VoiceEmotionLabel을 프롬프트에 주입할 한국어 텍스트 블록으로 변환.
     * VoiceComposite가 null이면 {@code "(음성 분석 미완료)"} 반환.
     */
    public String summarizeVoiceEmotion(VoiceComposite composite, List<VoiceEmotionLabel> labels) {
        if (composite == null) return "(음성 분석 미완료)";
        List<VoiceEmotionLabelView> views = labels == null ? List.of()
                : labels.stream()
                        .map(l -> new VoiceEmotionLabelView(l.getLabel(), l.getIntensityX1000()))
                        .toList();
        return summarizeVoiceEmotion(composite.getTopEmotion(),
                composite.getTopEmotionConfidenceBps(), views);
    }

    /**
     * 챗봇 음성(Flash 분석)의 경량 감정 데이터를 프롬프트 텍스트 블록으로 변환.
     * 마음일기 {@link VoiceComposite} 경로와 동일한 출력 포맷을 공유한다.
     * {@code topEmotion}이 null이면 {@code "(음성 분석 미완료)"} 반환.
     */
    public String summarizeVoiceEmotion(EmotionType topEmotion, Integer topEmotionConfidenceBps,
                                        List<VoiceEmotionLabelView> labels) {
        if (topEmotion == null) return "(음성 분석 미완료)";
        StringBuilder sb = new StringBuilder();
        sb.append("- 주된 감정: **").append(topEmotion.name().toLowerCase(Locale.ROOT)).append("**");
        sb.append(" (강도 bps: ").append(topEmotionConfidenceBps).append(")\n");
        if (labels != null && !labels.isEmpty()) {
            List<VoiceEmotionLabelView> top3 = labels.stream()
                    .sorted((a, b) -> Integer.compare(b.intensityX1000(), a.intensityX1000()))
                    .limit(3)
                    .toList();
            sb.append("- 세부 감정 top: ");
            for (int i = 0; i < top3.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(top3.get(i).label())
                        .append("(").append(top3.get(i).intensityX1000() / 1000.0).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * 프롬프트의 EmotionStrategies 블록 선택용 영문 카테고리 추출.
     * VoiceComposite가 null이면 null.
     */
    public String emotionHint(VoiceComposite composite) {
        return composite == null ? null : emotionHint(composite.getTopEmotion());
    }

    /** 챗봇 음성용 — 대표 감정 enum에서 직접 카테고리 추출. null이면 null. */
    public String emotionHint(EmotionType topEmotion) {
        return topEmotion == null ? null : topEmotion.name().toLowerCase(Locale.ROOT);
    }

    // -- bot_response JSON에서 단일 필드 추출 -------------------------------

    /**
     * 이전 봇 메시지를 history 텍스트로 표현. socratic_question을 우선,
     * 없으면 empathy. 모두 비면 빈 문자열.
     */
    public String botMessagePreview(JsonNode botResponse) {
        if (botResponse == null) return "";
        JsonNode q = botResponse.get("socratic_question");
        if (q != null && !q.isNull()) return q.asText();
        JsonNode e = botResponse.get("empathy");
        return e == null || e.isNull() ? "" : e.asText();
    }

    /**
     * bot_response의 "emotion" 또는 "top_emotion" 값을 추출. 없으면 null.
     */
    public String emotion(JsonNode botResponse) {
        if (botResponse == null) return null;
        JsonNode n = botResponse.get("emotion");
        if (n == null) n = botResponse.get("top_emotion");
        return n == null || n.isNull() ? null : n.asText();
    }

    /**
     * detected_distortion 단일 값을 단일 항목 List로 반환.
     * "없음", null, 빈 문자열은 빈 List.
     */
    public List<String> distortionTags(JsonNode botResponse) {
        if (botResponse == null) return List.of();
        JsonNode n = botResponse.get("detected_distortion");
        if (n == null || n.isNull()) return List.of();
        String value = n.asText();
        if (value.isBlank() || "없음".equals(value)) return List.of();
        return List.of(value);
    }
}
