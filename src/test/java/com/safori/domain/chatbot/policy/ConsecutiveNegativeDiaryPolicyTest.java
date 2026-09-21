package com.safori.domain.chatbot.policy;

import com.safori.domain.emotion.entity.EmotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ConsecutiveNegativeDiaryPolicyTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate MON = LocalDate.of(2026, 7, 6);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);
    private static final LocalDate SUN = MON.plusDays(6);

    private FakeHistory history;
    private SessionTriggerProperties props;
    private ConsecutiveNegativeDiaryPolicy policy;

    @BeforeEach
    void setUp() {
        history = new FakeHistory();
        props = new SessionTriggerProperties();
        policy = new ConsecutiveNegativeDiaryPolicy(props, history, new ContextWindowResolver(props));
    }

    private SessionTriggerDecision decideOn(LocalDate date) {
        DailyDiaryEmotion diary = history.on(date);
        return policy.decide(new SessionTriggerContext(
                USER_ID, diary.voiceId(), date, diary.emotion()));
    }

    @Nested
    @DisplayName("3일 연속 부정 감정 판정")
    class StreakDetection {

        @Test
        @DisplayName("월~일 내내 부정이면 3일째인 수요일부터 일요일까지 매일 세션이 생성된다")
        void createsFromThirdDayOnward() {
            history.negative(MON, TUE, WED, THU, FRI, SAT, SUN);

            assertThat(decideOn(MON).create()).isFalse();
            assertThat(decideOn(TUE).create()).isFalse();
            assertThat(decideOn(WED).create()).isTrue();
            assertThat(decideOn(THU).create()).isTrue();
            assertThat(decideOn(FRI).create()).isTrue();
            assertThat(decideOn(SAT).create()).isTrue();
            assertThat(decideOn(SUN).create()).isTrue();
        }

        @Test
        @DisplayName("중간에 긍정 감정이 끼면 연속이 끊겨 다시 3일을 채워야 한다")
        void positiveDayBreaksStreak() {
            history.negative(MON, TUE);
            history.positive(WED);
            history.negative(THU, FRI, SAT);

            assertThat(decideOn(WED).create()).isFalse();
            assertThat(decideOn(THU).create()).isFalse();  // 스트릭 1일차
            assertThat(decideOn(FRI).create()).isFalse();  // 2일차
            assertThat(decideOn(SAT).create()).isTrue();   // 3일차 → 생성
        }

        @Test
        @DisplayName("일기를 쓰지 않은 날이 있으면 연속이 끊긴다")
        void missingDayBreaksStreak() {
            history.negative(MON, TUE);
            // 수요일 일기 없음
            history.negative(THU, FRI);

            assertThat(decideOn(FRI).create()).isFalse();
        }

        @Test
        @DisplayName("트리거 일기가 부정이 아니면 LLM 호출 전에 즉시 중단된다")
        void nonNegativeTriggerSkipsImmediately() {
            history.negative(MON, TUE);
            history.positive(WED);

            SessionTriggerDecision decision = decideOn(WED);

            assertThat(decision.create()).isFalse();
            assertThat(decision.reasonCode()).isEqualTo("NOT_NEGATIVE");
            assertThat(history.queryCount).isZero();  // 감정 이력 조회조차 하지 않음
        }

        @Test
        @DisplayName("슬픔·분노·불안이 섞여 있어도 모두 부정이므로 연속으로 인정된다")
        void mixedNegativeEmotionsCountAsStreak() {
            history.put(MON, EmotionType.SAD);
            history.put(TUE, EmotionType.ANGRY);
            history.put(WED, EmotionType.ANXIETY);

            assertThat(decideOn(WED).create()).isTrue();
        }
    }

    @Nested
    @DisplayName("설정 변경으로 정책이 바뀐다 (배포 없이 대응 가능한 범위)")
    class ConfigDriven {

        @Test
        @DisplayName("consecutive-days를 5로 올리면 5일째부터 생성된다")
        void consecutiveDaysIsConfigurable() {
            props.setConsecutiveDays(5);
            history.negative(MON, TUE, WED, THU, FRI);

            assertThat(decideOn(THU).create()).isFalse();
            assertThat(decideOn(FRI).create()).isTrue();
        }

        @Test
        @DisplayName("negative-emotions에 NEUTRAL을 추가하면 중립도 부정으로 취급된다")
        void negativeEmotionSetIsConfigurable() {
            props.setNegativeEmotions(java.util.EnumSet.of(
                    EmotionType.SAD, EmotionType.ANGRY, EmotionType.ANXIETY, EmotionType.NEUTRAL));
            history.put(MON, EmotionType.SAD);
            history.put(TUE, EmotionType.NEUTRAL);
            history.put(WED, EmotionType.SAD);

            assertThat(decideOn(WED).create()).isTrue();
        }
    }

    @Nested
    @DisplayName("세션 컨텍스트 일기 범위")
    class ContextWindow {

        @Test
        @DisplayName("STREAK: 연속 구간 전체가 시간순으로 들어가고 마지막이 트리거 일기다")
        void streakIncludesWholeRun() {
            history.negative(MON, TUE, WED, THU);

            SessionTriggerDecision decision = decideOn(THU);

            assertThat(decision.contextVoiceIds()).containsExactly(
                    history.on(MON).voiceId(), history.on(TUE).voiceId(),
                    history.on(WED).voiceId(), history.on(THU).voiceId());
            assertThat(decision.triggerVoiceId()).isEqualTo(history.on(THU).voiceId());
        }

        @Test
        @DisplayName("STREAK: max-context-diaries를 넘으면 최신 쪽만 남는다")
        void streakIsCappedKeepingRecent() {
            props.setMaxContextDiaries(3);
            history.negative(MON, TUE, WED, THU, FRI, SAT, SUN);

            SessionTriggerDecision decision = decideOn(SUN);

            assertThat(decision.contextVoiceIds()).containsExactly(
                    history.on(FRI).voiceId(), history.on(SAT).voiceId(), history.on(SUN).voiceId());
        }

        @Test
        @DisplayName("TRIGGER_ONLY: 트리거 일기 1건만 들어간다")
        void triggerOnlyWindow() {
            props.setContextWindow(SessionTriggerProperties.ContextWindow.TRIGGER_ONLY);
            history.negative(MON, TUE, WED, THU);

            assertThat(decideOn(THU).contextVoiceIds())
                    .containsExactly(history.on(THU).voiceId());
        }

        @Test
        @DisplayName("FIXED_DAYS: 설정한 일수만큼만 들어간다")
        void fixedDaysWindow() {
            props.setContextWindow(SessionTriggerProperties.ContextWindow.FIXED_DAYS);
            props.setContextWindowDays(2);
            history.negative(MON, TUE, WED, THU);

            assertThat(decideOn(THU).contextVoiceIds())
                    .containsExactly(history.on(WED).voiceId(), history.on(THU).voiceId());
        }
    }

    /** 날짜 → 감정만 들고 있는 인메모리 이력. 마음일기는 하루 1건 정책이라 날짜당 1건. */
    private static class FakeHistory implements DiaryEmotionHistoryPort {

        private final Map<LocalDate, DailyDiaryEmotion> byDate = new TreeMap<>();
        private long nextVoiceId = 100L;
        private int queryCount = 0;

        void put(LocalDate date, EmotionType emotion) {
            byDate.put(date, new DailyDiaryEmotion(date, nextVoiceId++, emotion));
        }

        void negative(LocalDate... dates) {
            for (LocalDate d : dates) put(d, EmotionType.SAD);
        }

        void positive(LocalDate... dates) {
            for (LocalDate d : dates) put(d, EmotionType.HAPPY);
        }

        DailyDiaryEmotion on(LocalDate date) {
            return byDate.get(date);
        }

        @Override
        public List<Long> findUntriggeredVoiceIds(LocalDateTime since, int limit) {
            throw new UnsupportedOperationException("정책 판단에서 쓰지 않는다");
        }

        @Override
        public List<DailyDiaryEmotion> findDailyEmotions(Long userId, LocalDate from, LocalDate to) {
            queryCount++;
            List<DailyDiaryEmotion> result = new ArrayList<>();
            for (DailyDiaryEmotion d : byDate.values()) {
                if (!d.date().isBefore(from) && !d.date().isAfter(to)) result.add(d);
            }
            result.sort(Comparator.comparing(DailyDiaryEmotion::date).reversed());  // 최신순
            return result;
        }
    }
}
