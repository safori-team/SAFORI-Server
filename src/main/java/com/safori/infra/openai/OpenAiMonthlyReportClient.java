package com.safori.infra.openai;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.entity.Gender;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpenAiMonthlyReportClient {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public String generateMonthlyReport(String userName, Gender gender, Map<EmotionType, Long> counts) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        JsonNode response = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", monthlySystemPrompt()),
                                Map.of("role", "user", "content", buildUserPrompt(userName, gender, counts))
                        ),
                        "temperature", 0.7,
                        "max_completion_tokens", 400
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("OpenAI response is empty");
        }

        String content = response.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI monthly report content is empty");
        }
        return content;
    }

    private String monthlySystemPrompt() {
        return "너는 노년층 혹은 장애인 케어 서비스의 감정 코치다. 한국어로 공감적이고 자연스럽게, 1~3문장으로 " +
                "월간 감정 빈도 특성을 반드시 요약해라. 데이터가 적어도 관찰 가능한 내용을 바탕으로 요약을 제공해야 한다. " +
                "추측하지 말고 관찰적인 표현만 사용하고, 과장 없이 사실 중심으로 서술해라. 조언은 최소화하고 관찰 결과에 집중해라. " +
                "감정은 반드시 한국어(즐거움/슬픔/안정/분노/불안/놀람)로만 표기하고, 영어 감정 단어는 절대 쓰지 마라. " +
                "수치와 순위에 맞게 서술하고, 서로 다른 감정을 혼동하지 마라. " +
                "사용자를 지칭할 때는 사용자 프롬프트에 주어진 호칭을 그대로 사용한다.";
    }

    private String buildUserPrompt(String userName, Gender gender, Map<EmotionType, Long> counts) {
        String address = UserHonorific.of(userName, gender);
        Map<String, Integer> normalizedCounts = normalizeCounts(counts);
        int total = normalizedCounts.values().stream().mapToInt(Integer::intValue).sum();
        int denominator = total == 0 ? 1 : total;

        Map<String, Integer> percentages = new LinkedHashMap<>();
        normalizedCounts.forEach((emotion, count) ->
                percentages.put(emotion, (int) Math.round(count * 100.0 / denominator)));

        String ranked = normalizedCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));

        return "대상 사용자 호칭: " + address + "\n" +
                "총합: " + total + "건\n" +
                "정렬(내림차순): " + (ranked.isBlank() ? "(데이터 없음)" : ranked) + "\n" +
                "백분율: 즐거움=" + percentages.get("즐거움") + "%, " +
                "슬픔=" + percentages.get("슬픔") + "%, " +
                "안정=" + percentages.get("안정") + "%, " +
                "분노=" + percentages.get("분노") + "%, " +
                "불안=" + percentages.get("불안") + "%, " +
                "놀람=" + percentages.get("놀람") + "%\n" +
                "위 수치에 정확히 기반하여 월간 감정 경향을 1~3문장으로 요약해줘. " +
                "사용자를 반드시 '" + address + "'라고 지칭하고, '님' 등 다른 호칭은 덧붙이지 마. " +
                "순위나 비율과 모순되는 표현은 사용하지 마.";
    }

    private Map<String, Integer> normalizeCounts(Map<EmotionType, Long> counts) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        Arrays.asList("즐거움", "슬픔", "안정", "분노", "불안", "놀람")
                .forEach(label -> normalized.put(label, 0));

        counts.forEach((emotion, count) -> normalized.put(toPromptEmotion(emotion), Math.toIntExact(count)));
        return normalized;
    }

    private String toPromptEmotion(EmotionType emotionType) {
        return emotionType == null ? "중립" : EmotionKorean.of(emotionType);
    }
}
