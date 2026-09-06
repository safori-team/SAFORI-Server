package com.safori.infra.tts;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * GCP Cloud Text-to-Speech(Chirp3-HD) REST 클라이언트.
 *
 * <p>Chirp3-HD는 SSML을 지원하지 않으므로 평문(text)만 전송하며, pitch는 무시된다.
 * 조절 가능한 값은 speakingRate 뿐이다.
 *
 * <p>엔드포인트를 설정으로 빼둔 이유는 Chirp3-HD가 로케일에 따라 글로벌이 아닌
 * 리전 엔드포인트(예: {@code https://us-central1-texttospeech.googleapis.com/v1})를
 * 요구할 수 있기 때문이다. 하드코딩하면 그 경우 재배포가 필요하다.
 *
 * <p>{@code tts.api-key}가 비어있으면 빈이 생성되지 않는다.
 * 호출부는 {@code Optional}로 주입받아 미구성 시 TTS_SYNTHESIS_FAILED로 응답한다.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${tts.api-key:}'.isEmpty()")
public class CloudTextToSpeechClient {

    /**
     * API 키를 쿼리스트링(?key=) 대신 헤더로 보낸다.
     * URL에 실으면 프록시 접근 로그는 물론, I/O 예외 메시지에 요청 URI가 포함되면서
     * 키가 애플리케이션 로그에 찍힌다.
     */
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String LANGUAGE_CODE = "ko-KR";
    private static final String AUDIO_ENCODING = "MP3";

    private final String voice;
    private final double speakingRate;
    private final RestClient restClient;

    public CloudTextToSpeechClient(
            @Value("${tts.api-key}") String apiKey,
            @Value("${tts.base-url:https://texttospeech.googleapis.com/v1}") String baseUrl,
            @Value("${tts.voice:ko-KR-Chirp3-HD-Achernar}") String voice,
            @Value("${tts.speaking-rate:1.0}") double speakingRate,
            @Value("${tts.timeout-seconds:5}") long timeoutSeconds
    ) {
        this.voice = voice;
        this.speakingRate = speakingRate;

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .build();
    }

    public String getVoice() {
        return voice;
    }

    public double getSpeakingRate() {
        return speakingRate;
    }

    /**
     * 평문을 합성해 MP3 바이트를 반환한다.
     *
     * @param text 합성할 평문 (SSML 불가)
     * @return MP3 바이트
     * @throws TextToSpeechException GCP 비2xx 응답, 타임아웃, 빈 응답
     */
    public byte[] synthesize(String text) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/text:synthesize")
                    .body(Map.of(
                            "input", Map.of("text", text),
                            "voice", Map.of("languageCode", LANGUAGE_CODE, "name", voice),
                            "audioConfig", Map.of("audioEncoding", AUDIO_ENCODING, "speakingRate", speakingRate)
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException e) {
            log.warn("Cloud TTS 호출 실패 (voice={}, textLength={})", voice, text.length(), e);
            throw new TextToSpeechException("Cloud TTS 호출에 실패했습니다.", e);
        }

        if (response == null) {
            throw new TextToSpeechException("Cloud TTS 응답이 비어있습니다.");
        }

        String audioContent = response.path("audioContent").asText("");
        if (audioContent.isBlank()) {
            throw new TextToSpeechException("Cloud TTS 응답에 audioContent가 없습니다.");
        }

        try {
            return Base64.getDecoder().decode(audioContent);
        } catch (IllegalArgumentException e) {
            throw new TextToSpeechException("Cloud TTS audioContent 디코딩에 실패했습니다.", e);
        }
    }
}
