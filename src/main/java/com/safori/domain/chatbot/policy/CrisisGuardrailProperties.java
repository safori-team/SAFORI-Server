package com.safori.domain.chatbot.policy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    /** 문맥 분류 결과를 위기 흐름으로 채택할 최소 신뢰도(0~1). */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double classifierMinConfidence = 0.70;

    /**
     * 상담 모델 호출 전에 검사하는 고위험 실행 표현. 공백을 제거한 발화에 부분 문자열로 매칭한다
     * ("자살하려고 해요" → "자살하려고해요" → {@code 자살하려고} 히트).
     *
     * <p><b>관용구는 일부러 넣지 않았다.</b> "힘들어 죽겠다", "죽도록 피곤하다" 같은 표현은
     * 한국어 일상어라 넣는 순간 정상 상담이 계속 끊긴다. 대신 자·타해 의도가 문장에 직접
     * 드러나는 형태만 담는다.
     *
     * <p>여기서 못 잡는 완곡한 표현과 부정·과거·인용 문맥은 전용 문맥 분류기가 판단한다.
     * 이 목록은 오탐 가능성이 낮은 1차 그물이지 유일한 그물이 아니다.
     */
    private List<String> highRiskKeywords = List.of(
            "자살할거",
            "자살하려고",
            "자살해야겠",
            "지금죽으러",
            "죽어버릴거",
            "죽어야겠",
            "자해할거",
            "손목을그을",
            "손목그을",
            "목을맬거",
            "목매달거",
            "뛰어내릴거",
            "번개탄피우",
            "농약마시려고",
            "수면제모아",
            "유서를썼",
            "죽여버릴거",
            "다죽일거"
    );
}
