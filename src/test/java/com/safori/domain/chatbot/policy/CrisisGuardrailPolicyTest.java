package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.CrisisTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.GeneratedReply;
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
        policy = new CrisisGuardrailPolicy(props);
    }

    private static ChatbotReply reply(String distortion) {
        return new ChatbotReply("공감", distortion, "분석", "질문", "대안", "sad");
    }

    @Nested
    @DisplayName("사전 스크리닝 (LLM 호출 전)")
    class Screen {

        @ParameterizedTest
        @ValueSource(strings = {
                "요즘 자꾸 자살 생각이 나요",
                "그냥 죽고 싶어요",
                "이제 그만 살고 싶지 않아요",
                "다 사라지고 싶은 마음이에요",
                "자해를 한 적이 있어요"
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

        @Test
        @DisplayName("띄어쓰기가 달라도 같은 표현으로 본다")
        void ignoresWhitespace() {
            assertThat(policy.screen("죽고싶어요").detected()).isTrue();
            assertThat(policy.screen("죽 고  싶 어요").detected()).isTrue();
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
    }

    @Nested
    @DisplayName("사후 검사 (LLM 응답)")
    class Inspect {

        @Test
        @DisplayName("안전 필터에 차단되면 SAFETY_BLOCKED로 판정한다")
        void detectsSafetyBlock() {
            CrisisVerdict verdict = policy.inspect(
                    GeneratedReply.safetyBlocked("promptBlockReason=SAFETY"));

            assertThat(verdict.detected()).isTrue();
            assertThat(verdict.trigger()).isEqualTo(CrisisTrigger.SAFETY_BLOCKED);
            assertThat(verdict.detail()).isEqualTo("promptBlockReason=SAFETY");
        }

        @Test
        @DisplayName("모델이 '위기 상황'으로 판정하면 CRISIS_DISTORTION으로 잡는다")
        void detectsCrisisDistortion() {
            CrisisVerdict verdict = policy.inspect(GeneratedReply.ok(reply("위기 상황")));

            assertThat(verdict.detected()).isTrue();
            assertThat(verdict.trigger()).isEqualTo(CrisisTrigger.CRISIS_DISTORTION);
        }

        @Test
        @DisplayName("일반 인지 왜곡은 위기가 아니다")
        void ordinaryDistortionIsNotCrisis() {
            assertThat(policy.inspect(GeneratedReply.ok(reply("파국화"))).detected()).isFalse();
            assertThat(policy.inspect(GeneratedReply.ok(reply("없음"))).detected()).isFalse();
        }

        @Test
        @DisplayName("생성 실패(폴백)는 위기가 아니다 — 일시적 오류와 위기를 섞지 않는다")
        void generationFailureIsNotCrisis() {
            assertThat(policy.inspect(GeneratedReply.fallback()).detected()).isFalse();
        }

        @Test
        @DisplayName("가드레일을 끄면 안전 필터 차단도 위기로 보지 않는다")
        void disabledSkipsInspection() {
            props.setEnabled(false);

            assertThat(policy.inspect(GeneratedReply.safetyBlocked("blocked")).detected()).isFalse();
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
