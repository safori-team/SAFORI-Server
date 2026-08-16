package com.safori.domain.chatbot.policy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CBT 상담 위기 가드레일 설정.
 *
 * <p>env override 예: {@code CHAT_GUARDRAIL_ENABLED=false},
 * {@code CHAT_GUARDRAIL_KEYWORDS=자살,자해,번개탄}
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "safori.chatbot.guardrail")
public class CrisisGuardrailProperties {

    /**
     * 가드레일 전체 on/off. 끄면 위기 판정도, 위기 종료도 하지 않는다
     * (Gemini 안전 설정은 {@code gemini.safety.enabled}가 따로 관리한다).
     */
    private boolean enabled = true;

    /**
     * LLM 호출 전에 검사하는 고위험 표현. 공백을 제거한 발화에 부분 문자열로 매칭한다
     * ("죽고 싶어요" → "죽고싶어요" → {@code 죽고싶} 히트).
     *
     * <p><b>관용구는 일부러 넣지 않았다.</b> "힘들어 죽겠다", "죽도록 피곤하다" 같은 표현은
     * 한국어 일상어라 넣는 순간 정상 상담이 계속 끊긴다. 대신 자·타해 의도가 문장에 직접
     * 드러나는 형태만 담는다.
     *
     * <p>여기서 못 잡는 완곡한 표현은 Gemini 안전 필터({@code SAFETY_BLOCKED})와
     * 모델의 위기 판정({@code CRISIS_DISTORTION})이 뒤에서 받는다. 이 목록은 1차 그물이지
     * 유일한 그물이 아니다.
     */
    private List<String> highRiskKeywords = List.of(
            "자살",
            "죽고싶",
            "죽어버리",
            "죽어야겠",
            "자해",
            "손목을긋",
            "손목긋",
            "목을매",
            "목매달",
            "뛰어내리",
            "번개탄",
            "농약을마시",
            "수면제를모",
            "유서를",
            "살고싶지않",
            "사라지고싶",
            "없어지고싶",
            "따라죽",
            "죽여버리",
            "다죽여"
    );
}
