package com.safori.infra.ai.gemini;

import com.safori.domain.chatbot.model.GeneratedReply;
import com.safori.infra.ai.gemini.config.GeminiSafetyProperties;
import com.safori.infra.ai.gemini.prompts.DoranResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.Part;
import com.google.genai.types.SafetyRating;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeminiChatbotClient {

    private static final List<String> DISTORTION_ENUM = List.of(
            "흑백사고", "선택적 추상", "자의적 추론", "과잉일반화", "확대/축소",
            "개인화", "정서적 추론", "긍정 격하", "파국화", "잘못된 별칭 붙이기",
            "긍정 정서 강화", "위기 상황", "없음"
    );

    private static final List<String> EMOTION_ENUM = List.of(
            "happy", "sad", "neutral", "angry", "anxiety", "surprise"
    );

    /**
     * 안전 필터가 생성을 끊었음을 뜻하는 finishReason. 나머지(STOP, MAX_TOKENS 등)는
     * 차단이 아니므로 일반 실패 경로로 보낸다.
     */
    private static final Set<FinishReason.Known> BLOCKING_FINISH_REASONS = Set.of(
            FinishReason.Known.SAFETY,
            FinishReason.Known.PROHIBITED_CONTENT,
            FinishReason.Known.BLOCKLIST,
            FinishReason.Known.SPII
    );

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final ObjectMapper objectMapper;
    private final GeminiSafetyProperties safetyProperties;

    public GeminiChatbotClient(
            Optional<Client> geminiClient,
            @Value("${gemini.chatbot-model:gemini-2.5-pro}") String modelName,
            ObjectMapper objectMapper,
            GeminiSafetyProperties safetyProperties
    ) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.objectMapper = objectMapper;
        this.safetyProperties = safetyProperties;
    }

    /**
     * 상담 응답 생성. 호출 실패·빈 응답·파싱 오류는 예외로 던지지 않고 폴백 응답으로 대체하되,
     * {@link GeneratedReply#fallback()}로 표시해 호출자가 메시지를 FAILED로 기록할 수 있게 한다.
     *
     * <p>안전 필터에 걸린 경우는 실패와 구분해 {@link GeneratedReply#safetyBlocked(String)}로
     * 반환한다 — 호출자(가드레일)가 이걸 보고 상담 자체를 중단시킨다. 폴백 멘트("잠시 생각이
     * 꼬였나 봐요")로 뭉뚱그리면 위기 상황이 일시적 오류처럼 흘러가 버린다.
     */
    public GeneratedReply generate(String prompt) {
        if (geminiClient.isEmpty()) {
            log.warn("GeminiChatbotClient: no Gemini client configured, returning fallback");
            return GeneratedReply.fallback();
        }
        try {
            GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                    .temperature(0.7f)
                    // gemini-2.5-pro는 thinking 모델 — thinking 토큰이 maxOutputTokens에 포함된다.
                    // 2048로는 thinking 후 JSON 출력이 잘려(JsonEOFException) 폴백으로 빠지므로,
                    // 출력 한도를 넉넉히 주고 thinking 예산을 캡해 실제 응답 토큰을 확보한다.
                    .maxOutputTokens(8192)
                    .thinkingConfig(ThinkingConfig.builder().thinkingBudget(2048).build())
                    .responseMimeType("application/json")
                    .responseSchema(buildResponseSchema());

            // 빈 목록을 실으면 "모든 카테고리 미지정"이 아니라 빈 배열이 전송된다.
            // 끈 상태(enabled=false)에서는 필드 자체를 빼 모델 기본값에 맡긴다.
            List<SafetySetting> safetySettings = safetyProperties.toSafetySettings();
            if (!safetySettings.isEmpty()) {
                config.safetySettings(safetySettings);
            }

            GenerateContentResponse response = geminiClient.get().models.generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text(prompt).build()))
                            .build(),
                    config.build()
            );

            // 차단 여부를 text()보다 먼저 본다 — 차단되면 후보에 content가 없어
            // text()가 null을 주거나 예외를 던져 원인이 뭉개진다.
            String blockDetail = detectSafetyBlock(response);
            if (blockDetail != null) {
                log.warn("GeminiChatbotClient: blocked by safety filter — {}", blockDetail);
                return GeneratedReply.safetyBlocked(blockDetail);
            }

            String text = response.text();
            if (text == null || text.isBlank()) {
                log.warn("GeminiChatbotClient: empty response, returning fallback");
                return GeneratedReply.fallback();
            }
            return GeneratedReply.ok(objectMapper.readValue(text, DoranResponse.class).toReply());
        } catch (Exception e) {
            log.error("GeminiChatbotClient.generate failed", e);
            return GeneratedReply.fallback();
        }
    }

    /**
     * 안전 필터 차단을 감지한다. 차단 지점이 둘이라 양쪽을 다 본다:
     * <ul>
     *   <li>프롬프트 차단 — {@code promptFeedback.blockReason} (응답 후보 자체가 없다)</li>
     *   <li>응답 차단 — {@code candidates[].finishReason} = SAFETY 등</li>
     * </ul>
     *
     * @return 차단 근거 문자열, 차단이 아니면 null
     */
    private String detectSafetyBlock(GenerateContentResponse response) {
        Optional<GenerateContentResponsePromptFeedback> feedback = response.promptFeedback();
        if (feedback.isPresent() && feedback.get().blockReason().isPresent()) {
            return "promptBlockReason=" + feedback.get().blockReason().get()
                    + ratingsOf(feedback.get().safetyRatings().orElse(List.of()));
        }

        for (Candidate candidate : response.candidates().orElse(List.of())) {
            Optional<FinishReason> finishReason = candidate.finishReason();
            if (finishReason.isPresent()
                    && BLOCKING_FINISH_REASONS.contains(finishReason.get().knownEnum())) {
                return "finishReason=" + finishReason.get()
                        + ratingsOf(candidate.safetyRatings().orElse(List.of()));
            }
        }
        return null;
    }

    /** 실제로 차단된 카테고리만 추린다 — 어떤 축에서 걸렸는지 로그로 남기기 위해서다. */
    private String ratingsOf(List<SafetyRating> ratings) {
        String blocked = ratings.stream()
                .filter(r -> r.blocked().orElse(false))
                .map(r -> r.category().map(Object::toString).orElse("UNKNOWN")
                        + "(" + r.probability().map(Object::toString).orElse("?") + ")")
                .collect(Collectors.joining(","));
        return blocked.isEmpty() ? "" : " categories=[" + blocked + "]";
    }

    private Schema buildResponseSchema() {
        Map<String, Schema> props = new HashMap<>();
        props.put("empathy", Schema.builder().type(Type.Known.STRING).build());
        props.put("detected_distortion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(DISTORTION_ENUM)
                .build());
        props.put("analysis", Schema.builder().type(Type.Known.STRING).build());
        props.put("socratic_question", Schema.builder().type(Type.Known.STRING).build());
        props.put("alternative_thought", Schema.builder().type(Type.Known.STRING).build());
        props.put("top_emotion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(EMOTION_ENUM)
                .build());

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(props)
                .required(List.of("empathy", "detected_distortion", "analysis",
                        "socratic_question", "alternative_thought", "top_emotion"))
                .build();
    }
}
