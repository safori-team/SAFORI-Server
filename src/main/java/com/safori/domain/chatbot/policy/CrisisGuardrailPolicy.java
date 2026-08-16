package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.GeneratedReply;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * CBT 상담 위기 가드레일.
 *
 * <p>상담(counseling)과 치료(treatment)의 경계를 서버가 판정한다. 자·타해 의도가 드러난
 * 내담자에게 도란이가 소크라테스식 질문을 계속 던지는 것은 도움이 아니라 위험이므로,
 * 걸리는 즉시 상담을 끝내고 전문 기관 안내로 갈아탄다.
 *
 * <p><b>종료 방식은 턴 소진과 동일하다</b> — 프론트는 {@code sessionClosed=true}만 보고
 * 입력창을 닫으면 되고, {@code crisisDetected=true}로 안내 배너를 띄울지만 추가로 정하면 된다.
 * 다만 턴 소진은 메시지 수로 매번 다시 계산되는 반면, 위기 종료는
 * {@link ChatSession#closeByCrisis} 상태로 남아야 재계산으로 복원된다.
 *
 * <p>검사 지점은 두 곳이다:
 * <pre>
 *   {@link #screen(String)}   LLM 호출 전  — 발화 자체가 고위험 (토큰 0으로 차단)
 *   {@link #inspect(GeneratedReply)}  LLM 호출 후 — 안전 필터 차단 또는 모델의 위기 판정
 * </pre>
 *
 * <p>가드레일을 프롬프트 지시에만 맡기지 않는 이유는 턴 제한과 같다 — 지시는 모델이 무시할
 * 수 있지만, 여기서 강제하면 무시할 수 없다. 프롬프트의 위기 개입 지시는 폐기하지 않고
 * 세 번째 그물({@link CrisisTrigger#CRISIS_DISTORTION})로 계속 쓴다.
 */
@Component
@RequiredArgsConstructor
public class CrisisGuardrailPolicy {

    /** 모델이 스스로 위기로 판정했을 때 {@code detected_distortion}에 담기는 값. */
    private static final String CRISIS_DISTORTION_LABEL = "위기 상황";

    private final CrisisGuardrailProperties props;

    /**
     * 이미 가드레일로 닫힌 세션인지 검증한다. 턴 검증보다 <b>먼저</b> 부르는 것을 전제로 한다 —
     * 위기로 닫힌 세션은 턴이 남아 있어도 열리지 않기 때문이다.
     *
     * @throws com.safori.common.exception.GeneralException 위기로 종료된 세션이면
     */
    public void verifyNotCrisisClosed(ChatSession session) {
        if (session.isCrisisClosed()) {
            throw ChatbotHandler.SESSION_CRISIS_CLOSED;
        }
    }

    /**
     * LLM 호출 전 사전 스크리닝. 걸리면 상담 응답을 생성하지 않고 바로 위기 안내로 답한다.
     *
     * <p>Gemini 안전 필터보다 앞에 두는 이유는 셋이다 — 위험 발화를 모델에 보내지 않고,
     * 토큰을 쓰지 않으며, 안전 필터가 놓치는(자살 암시는 "위험 콘텐츠 생성"이 아니라
     * 종종 통과한다) 사각을 메운다.
     */
    public CrisisVerdict screen(String userInput) {
        if (!props.isEnabled() || userInput == null || userInput.isBlank()) {
            return CrisisVerdict.none();
        }
        String normalized = normalize(userInput);
        for (String keyword : props.getHighRiskKeywords()) {
            if (keyword != null && !keyword.isBlank() && normalized.contains(normalize(keyword))) {
                return CrisisVerdict.of(CrisisTrigger.HIGH_RISK_KEYWORD, "keyword=" + keyword);
            }
        }
        return CrisisVerdict.none();
    }

    /**
     * LLM 응답 검사. 안전 필터 차단이 먼저다 — 차단된 경우 응답 본문이 없어
     * {@code detected_distortion}을 볼 수조차 없다.
     */
    public CrisisVerdict inspect(GeneratedReply generated) {
        if (!props.isEnabled() || generated == null) {
            return CrisisVerdict.none();
        }
        if (generated.safetyBlocked()) {
            return CrisisVerdict.of(CrisisTrigger.SAFETY_BLOCKED, generated.safetyDetail());
        }
        if (generated.reply() != null
                && CRISIS_DISTORTION_LABEL.equals(generated.reply().detectedDistortion())) {
            return CrisisVerdict.of(CrisisTrigger.CRISIS_DISTORTION,
                    "detected_distortion=" + CRISIS_DISTORTION_LABEL);
        }
        return CrisisVerdict.none();
    }

    /** 공백·대소문자 차이를 없앤다 — "죽고 싶어요"와 "죽고싶어요"를 같은 것으로 본다. */
    private String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
