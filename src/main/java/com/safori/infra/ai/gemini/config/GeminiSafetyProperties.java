package com.safori.infra.ai.gemini.config;

import com.google.genai.types.HarmCategory;
import com.google.genai.types.SafetySetting;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gemini 안전 필터(safety settings) 임계값.
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/safety-settings">Gemini 안전 설정 문서</a>
 *
 * <p>임계값을 명시하지 않으면 모델별 기본값이 적용되는데, gemini-2.5 계열은 대부분의 카테고리에서
 * 사실상 차단하지 않는다. CBT 상담은 위험 발화가 실제로 오가는 도메인이라 여기서 명시적으로
 * 조인다.
 *
 * <p><b>임계값 선택의 트레이드오프.</b> 노인 상담에서는 사별·질병·죽음 이야기가 정상적으로
 * 오간다("남편이 작년에 돌아가셨어요"). {@code BLOCK_LOW_AND_ABOVE}로 조이면 이런 발화까지
 * 차단돼 멀쩡한 상담이 끊긴다. 그래서 기본값은 자·타해 신호가 실제로 뚜렷할 때만 걸리도록
 * {@code BLOCK_MEDIUM_AND_ABOVE}(위험 콘텐츠·괴롭힘)와 {@code BLOCK_ONLY_HIGH}(사별·질병
 * 대화가 오인되기 쉬운 나머지) 사이로 잡았고, 전부 env로 조절할 수 있다.
 *
 * <p>안전 필터는 가드레일의 <b>두 번째</b> 그물일 뿐이다. "죽고 싶다"는 위험 콘텐츠 '생성'이
 * 아니라서 통과하는 경우가 많아, 서버 사전 스크리닝
 * ({@link com.safori.domain.chatbot.policy.CrisisGuardrailPolicy#screen})이 앞에서 받는다.
 *
 * <p>허용 값: {@code BLOCK_NONE}, {@code BLOCK_ONLY_HIGH}, {@code BLOCK_MEDIUM_AND_ABOVE},
 * {@code BLOCK_LOW_AND_ABOVE}, {@code OFF}
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gemini.safety")
public class GeminiSafetyProperties {

    /** 끄면 안전 설정을 요청에 싣지 않는다 (모델 기본값 적용). */
    private boolean enabled = true;

    /** 자·타해·자살 암시가 걸리는 핵심 카테고리. 가장 먼저 조인다. */
    private String dangerousContent = "BLOCK_MEDIUM_AND_ABOVE";

    /** 타인을 향한 과격한 언행. */
    private String harassment = "BLOCK_MEDIUM_AND_ABOVE";

    /** 혐오 표현. 사별·질병 서사가 오인될 여지가 있어 한 단계 느슨하게 둔다. */
    private String hateSpeech = "BLOCK_ONLY_HIGH";

    /** 성적 노골성. 상담 맥락에서 나올 일이 거의 없어 느슨하게 둔다. */
    private String sexuallyExplicit = "BLOCK_ONLY_HIGH";

    /**
     * 요청에 실을 {@link SafetySetting} 목록. {@code enabled=false}면 빈 목록.
     *
     * <p>임계값 문자열은 SDK에 그대로 넘긴다 — 오타는 API 400으로 드러나므로 여기서 다시
     * 검증하지 않는다(SDK가 미래에 추가할 값을 앱 배포 없이 쓸 수 있는 이점이 더 크다).
     */
    public List<SafetySetting> toSafetySettings() {
        if (!enabled) {
            return List.of();
        }
        List<SafetySetting> settings = new ArrayList<>(4);
        add(settings, HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT, dangerousContent);
        add(settings, HarmCategory.Known.HARM_CATEGORY_HARASSMENT, harassment);
        add(settings, HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH, hateSpeech);
        add(settings, HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT, sexuallyExplicit);
        return settings;
    }

    private void add(List<SafetySetting> settings, HarmCategory.Known category, String threshold) {
        if (threshold == null || threshold.isBlank()) {
            return;   // 빈 값 = 이 카테고리는 모델 기본값에 맡긴다
        }
        settings.add(SafetySetting.builder()
                .category(category)
                .threshold(threshold.trim().toUpperCase(Locale.ROOT))
                .build());
    }
}
