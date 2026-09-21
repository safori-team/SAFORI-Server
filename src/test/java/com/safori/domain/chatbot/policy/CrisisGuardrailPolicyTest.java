package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.CrisisAssessment;
import com.safori.domain.chatbot.model.CrisisLevel;
import com.safori.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrisisGuardrailPolicyTest {

    private CrisisGuardrailProperties props;
    private CrisisGuardrailPolicy policy;

    @BeforeEach
    void setUp() {
        props = new CrisisGuardrailProperties();
        policy = new CrisisGuardrailPolicy(props, input -> CrisisAssessment.unavailable());
    }

    @Nested
    @DisplayName("사전 스크리닝 (LLM 호출 전)")
    class Screen {

        @ParameterizedTest
        @ValueSource(strings = {
                "오늘 자살하려고 해요",
                "이제 죽어야겠어요",
                "지금 뛰어내릴 거예요",
                "이미 유서를 썼어요",
                "그 사람을 죽여버릴 거예요"
        })
        @DisplayName("자·타해 의도가 드러난 발화는 LLM을 거치기 전에 걸린다")
        void detectsHighRiskUtterances(String input) {
            CrisisVerdict verdict = policy.screen(input);

            assertThat(verdict.detected()).isTrue();
            assertThat(verdict.trigger()).isEqualTo(CrisisTrigger.HIGH_RISK_KEYWORD);
            assertThat(verdict.detail()).startsWith("keyword=");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "아이고 힘들어 죽겠어요",
                "요즘 무릎이 아파 죽겠네",
                "손주 보느라 바빠 죽겠어요",
                "남편이 작년에 돌아가셨어요",
                "장례식에 다녀왔는데 마음이 허해요"
        })
        @DisplayName("'죽겠다' 관용구와 사별 이야기는 걸리지 않는다 — 노인 상담에서 일상적으로 나오는 말이다")
        void doesNotFlagKoreanIdiomsOrBereavement(String input) {
            assertThat(policy.screen(input).detected()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "예전에는 자해를 한 적이 있어요",
                "친구가 죽고 싶다고 했어요",
                "저는 죽고 싶지 않아요"
        })
        @DisplayName("넓은 단어 조각만으로 과거·인용·부정 표현을 즉시 차단하지 않는다")
        void broadTermsRequireContextClassification(String input) {
            assertThat(policy.screen(input).detected()).isFalse();
        }

        @Test
        @DisplayName("띄어쓰기가 달라도 같은 표현으로 본다")
        void ignoresWhitespace() {
            assertThat(policy.screen("죽어야겠어요").detected()).isTrue();
            assertThat(policy.screen("죽 어 야 겠 어요").detected()).isTrue();
        }

        @Test
        @DisplayName("빈 발화·null은 판정하지 않는다")
        void ignoresBlankInput() {
            assertThat(policy.screen(null).detected()).isFalse();
            assertThat(policy.screen("   ").detected()).isFalse();
        }

        @Test
        @DisplayName("가드레일을 끄면 고위험 발화도 통과시킨다")
        void disabledLetsEverythingThrough() {
            props.setEnabled(false);

            assertThat(policy.screen("자살하고 싶어요").detected()).isFalse();
        }

        @Test
        @DisplayName("키워드 목록은 설정으로 교체할 수 있다")
        void keywordsAreConfigurable() {
            props.setHighRiskKeywords(List.of("특정표현"));

            assertThat(policy.screen("자살 생각이 나요").detected()).isFalse();
            assertThat(policy.screen("특정표현이 담긴 말").detected()).isTrue();
        }

        @Test
        @DisplayName("키워드가 없어도 문맥 분류기가 현재의 적극적 의도로 판정하면 걸린다")
        void detectsContextualActiveIntent() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.ACTIVE_INTENT, true, true, false, 0.93, "CURRENT_PLAN"));

            CrisisVerdict verdict = policy.screen("죽을 준비를 마치고 실행하려고 해요");

            assertThat(verdict.detected()).isTrue();
            assertThat(verdict.trigger()).isEqualTo(CrisisTrigger.AI_CRISIS_CLASSIFIER);
            assertThat(verdict.detail()).contains("level=ACTIVE_INTENT", "reason=CURRENT_PLAN");
        }

        @Test
        @DisplayName("현재의 수동적 자살 사고도 현재 지원 가능한 위기 안내 흐름으로 보낸다")
        void detectsCurrentPassiveIdeation() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.PASSIVE_IDEATION, true, false, false, 0.91, "PASSIVE_DEATH_WISH"));

            CrisisVerdict verdict = policy.screen("그냥 사라지고 싶어요");

            assertThat(verdict.detected()).isTrue();
            assertThat(verdict.trigger()).isEqualTo(CrisisTrigger.AI_CRISIS_CLASSIFIER);
        }

        @Test
        @DisplayName("계획과 수단이 없는 명령형 욕설·저주는 타해 위기로 막지 않는다")
        void insultWithoutPlanIsNotHarmIntent() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.HARM_TO_OTHERS, true, false, false, 0.90,
                    "HARM_TO_OTHERS_INTENT"));

            assertThat(policy.screen("니미 다 뒤져버려라").detected()).isFalse();
        }

        @Test
        @DisplayName("구체적 계획이 확인된 현재 타해 의도는 위기 흐름으로 보낸다")
        void harmIntentWithPlanIsDetected() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.HARM_TO_OTHERS, true, true, false, 0.90, "CONCRETE_HARM_PLAN"));

            assertThat(policy.screen("칼 들고 찾아갈 계획을 밝힌 문장").detected()).isTrue();
        }

        @Test
        @DisplayName("위험 후보 신호가 없는 욕설은 문맥 분류기를 호출하지 않고 통과한다")
        void profanityWithoutRiskMarkerSkipsClassifier() {
            java.util.concurrent.atomic.AtomicBoolean called =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            policy = new CrisisGuardrailPolicy(props, input -> {
                called.set(true);
                return new CrisisAssessment(CrisisLevel.ACTIVE_INTENT, true,
                        false, false, 0.99, "WRONG_INTENT");
            });

            assertThat(policy.screen("씨발거 한번 해보죠").detected()).isFalse();
            assertThat(called).isFalse();
        }

        @Test
        @DisplayName("과거·타인 인용처럼 현재 위험이 아니면 적극적 레벨이어도 통과한다")
        void ignoresNonCurrentAssessment() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.ACTIVE_INTENT, false, true, false, 0.95, "PAST_OR_QUOTED"));

            assertThat(policy.screen("친구가 예전에 그런 생각을 했대요").detected()).isFalse();
        }

        @Test
        @DisplayName("신뢰도가 임계값보다 낮으면 정상 상담을 막지 않는다")
        void ignoresLowConfidenceAssessment() {
            policy = new CrisisGuardrailPolicy(props, input -> new CrisisAssessment(
                    CrisisLevel.IMMINENT, true, true, true, 0.69, "AMBIGUOUS"));

            assertThat(policy.screen("애매한 표현").detected()).isFalse();
        }

        @Test
        @DisplayName("분류기를 사용할 수 없어도 정상 상담 흐름으로 통과한다")
        void classifierUnavailableFailsOpen() {
            assertThat(policy.screen("키워드 없는 평범한 발화").detected()).isFalse();
        }
    }

    @Nested
    @DisplayName("위기 종료 세션 차단")
    class VerifyNotCrisisClosed {

        @Test
        @DisplayName("위기로 닫힌 세션에 발화하면 CHAT_SESSION_CRISIS_CLOSED로 거부된다")
        void rejectsCrisisClosedSession() {
            ChatSession session = ChatSession.create(User.builder().build());
            session.closeByCrisis(CrisisTrigger.HIGH_RISK_KEYWORD);

            assertThatThrownBy(() -> policy.verifyNotCrisisClosed(session))
                    .isSameAs(ChatbotHandler.SESSION_CRISIS_CLOSED);
        }

        @Test
        @DisplayName("정상 세션은 통과한다")
        void allowsNormalSession() {
            ChatSession session = ChatSession.create(User.builder().build());

            assertThatCode(() -> policy.verifyNotCrisisClosed(session)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("한 번 닫힌 세션은 최초 발동 원인을 유지한다 — 사후 분석에 최초 신호가 더 쓸모 있다")
        void keepsFirstTrigger() {
            ChatSession session = ChatSession.create(User.builder().build());
            session.closeByCrisis(CrisisTrigger.HIGH_RISK_KEYWORD);
            session.closeByCrisis(CrisisTrigger.SAFETY_BLOCKED);

            assertThat(session.getCrisisTrigger()).isEqualTo(CrisisTrigger.HIGH_RISK_KEYWORD);
            assertThat(session.getCrisisDetectedAt()).isNotNull();
        }
    }
}
