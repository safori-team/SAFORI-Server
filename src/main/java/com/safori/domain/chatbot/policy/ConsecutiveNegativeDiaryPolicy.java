package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.policy.SessionTriggerProperties.PolicyType;
import com.safori.domain.emotion.entity.EmotionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * N일 연속으로 마음일기 대표 감정이 부정(슬픔/분노/불안)일 때 세션을 생성한다.
 *
 * <p>연속 구간은 트리거 일기 날짜에서 과거로 거슬러 계산한다. 하루라도 일기가 없거나
 * 부정이 아니면 거기서 끊긴다. 매일 재평가되므로 월~일 내내 부정이면 3일째인 수요일부터
 * 일요일까지 매일 세션이 생성된다.
 */
@Component
@RequiredArgsConstructor
public class ConsecutiveNegativeDiaryPolicy implements SessionTriggerPolicy {

    private final SessionTriggerProperties props;
    private final DiaryEmotionHistoryPort historyPort;
    private final ContextWindowResolver windowResolver;

    @Override
    public PolicyType type() {
        return PolicyType.CONSECUTIVE_NEGATIVE;
    }

    @Override
    public SessionTriggerDecision decide(SessionTriggerContext ctx) {
        if (!isNegative(ctx.topEmotion())) {
            return SessionTriggerDecision.skip("NOT_NEGATIVE");
        }

        List<DailyDiaryEmotion> streak = negativeStreakEndingAt(ctx);
        if (streak.size() < props.getConsecutiveDays()) {
            return SessionTriggerDecision.skip("STREAK_TOO_SHORT_" + streak.size());
        }

        return SessionTriggerDecision.create(
                "CONSECUTIVE_NEGATIVE_" + props.getConsecutiveDays() + "D_STREAK_" + streak.size(),
                windowResolver.resolve(streak, ctx));
    }

    /**
     * 트리거 일기 날짜부터 과거로 끊기지 않고 이어지는 부정 감정 구간.
     * 반환은 날짜 내림차순(트리거 일기가 첫 원소).
     */
    private List<DailyDiaryEmotion> negativeStreakEndingAt(SessionTriggerContext ctx) {
        // 판단(consecutiveDays)과 컨텍스트(maxContextDiaries) 중 더 넓은 쪽까지 한 번에 조회
        int lookbackDays = Math.max(props.getConsecutiveDays(), props.getMaxContextDiaries());
        LocalDate triggerDate = ctx.diaryDate();
        LocalDate from = triggerDate.minusDays(lookbackDays - 1L);

        Map<LocalDate, DailyDiaryEmotion> byDate = new HashMap<>();
        for (DailyDiaryEmotion d : historyPort.findDailyEmotions(ctx.userId(), from, triggerDate)) {
            // 조회 결과는 최신순 — 같은 날짜가 둘 이상이면(정책상 없어야 함) 최신 것만 남긴다
            byDate.putIfAbsent(d.date(), d);
        }

        List<DailyDiaryEmotion> streak = new ArrayList<>();
        for (int i = 0; i < lookbackDays; i++) {
            DailyDiaryEmotion day = byDate.get(triggerDate.minusDays(i));
            if (day == null || !isNegative(day.emotion())) break;
            streak.add(day);
        }
        return streak;
    }

    private boolean isNegative(EmotionType emotion) {
        return emotion != null && props.getNegativeEmotions().contains(emotion);
    }
}
