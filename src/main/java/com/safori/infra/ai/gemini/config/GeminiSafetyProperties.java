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
 * 사실상 차단하지 않는다. CBT 상담은 욕설·죽음·사별 등 민감한 발화도 입력으로 받아야 하므로
 * 기본값은 비활성화하고, 사용자 위기는 전용 문맥 분류기가 별도로 판단한다.
 *
 * <p><b>임계값 선택의 트레이드오프.</b> 노인 상담에서는 사별·질병·죽음 이야기가 정상적으로
 * 오간다("남편이 작년에 돌아가셨어요"). {@code BLOCK_LOW_AND_ABOVE}로 조이면 이런 발화까지
 * 차단돼 멀쩡한 상담이 끊긴다. 그래서 기본값은 자·타해 신호가 실제로 뚜렷할 때만 걸리도록
 * {@code BLOCK_MEDIUM_AND_ABOVE}(위험 콘텐츠·괴롭힘)와 {@code BLOCK_ONLY_HIGH}(사별·질병
 * 대화가 오인되기 쉬운 나머지) 사이로 잡았고, 전부 env로 조절할 수 있다.
 *
 * <p>안전 필터 차단은 사용자의 위기 상태를 뜻하지 않는다. 운영 정책상 생성 필터가 필요할 때만
 * {@code GEMINI_SAFETY_ENABLED=true}로 켠다.
 *
 * <p>허용 값: {@code BLOCK_NONE}, {@code BLOCK_ONLY_HIGH}, {@code BLOCK_MEDIUM_AND_ABOVE},
 * {@code BLOCK_LOW_AND_ABOVE}, {@code OFF}
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gemini.safety")
public class GeminiSafetyProperties {

    /** 끄면 안전 설정을 요청에 싣지 않는다 (모델 기본값 적용). */
    private boolean enabled = false;

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
