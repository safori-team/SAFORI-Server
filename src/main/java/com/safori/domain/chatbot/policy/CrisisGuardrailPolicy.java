package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.CrisisAssessment;
import com.safori.domain.chatbot.service.CrisisClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.List;

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
 * <p>가드레일을 프롬프트 지시에만 맡기지 않는 이유는 턴 제한과 같다 — 지시는 모델이 무시할
 * 수 있지만, 여기서 강제하면 무시할 수 없다. 위기 판정은 상담 응답 생성 전에 한 번만 확정한다.
 */
@Component
@RequiredArgsConstructor
public class CrisisGuardrailPolicy {

    /**
     * 문맥 분류를 호출할 넓은 후보 신호. 이것만으로는 절대 차단하지 않고 비용·오탐을 줄이는
     * 게이트로만 쓴다. 욕설 일반은 포함하지 않는다.
     */
    private static final List<String> CRISIS_CANDIDATE_MARKERS = List.of(
            "죽", "자살", "자해", "살고싶지않", "사라지고싶", "없어지고싶",
            "손목", "목매", "뛰어내", "번개탄", "농약", "수면제", "유서",
            "해치", "죽이", "찌르", "칼들고", "불질러", "폭발", "공격"
    );

    private final CrisisGuardrailProperties props;
    private final CrisisClassifier crisisClassifier;

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
     * 상담 LLM 호출 전 사전 스크리닝. 고정밀 키워드를 먼저 검사하고, 통과한 발화만
     * 전용 문맥 분류기로 보내 완곡한 적극적 자·타해 의도를 찾는다.
     *
     * <p>분류기 장애·파싱 실패는 정상 상담까지 막지 않도록 fail-open 한다.
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

        if (CRISIS_CANDIDATE_MARKERS.stream().noneMatch(normalized::contains)) {
            return CrisisVerdict.none();
        }

        CrisisAssessment assessment = crisisClassifier.classify(userInput);
        if (assessment.requiresCrisisFlow()
                && assessment.confidence() >= props.getClassifierMinConfidence()) {
            String detail = "level=" + assessment.level()
                    + ",confidence=" + assessment.confidence()
                    + ",plan=" + assessment.hasPlan()
                    + ",means=" + assessment.hasMeans()
                    + ",reason=" + assessment.reasonCode();
            return CrisisVerdict.of(CrisisTrigger.AI_CRISIS_CLASSIFIER, detail);
        }
        return CrisisVerdict.none();
    }

    /** 공백·대소문자 차이를 없앤다 — "죽고 싶어요"와 "죽고싶어요"를 같은 것으로 본다. */
    private String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
