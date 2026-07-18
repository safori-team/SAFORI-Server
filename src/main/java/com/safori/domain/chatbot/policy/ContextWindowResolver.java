package com.safori.domain.chatbot.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 세션 프롬프트에 넣을 일기 범위를 설정값에 따라 산출한다.
 * 반환 순서는 항상 시간순(오래된 → 최신)이며 마지막 원소가 트리거 일기다.
 */
@Component
@RequiredArgsConstructor
public class ContextWindowResolver {

    private final SessionTriggerProperties props;

    /**
     * @param streak  트리거 일기부터 과거로 이어지는 연속 구간 (날짜 내림차순, 첫 원소가 트리거 일기)
     * @param context 트리거 컨텍스트
     */
    public List<Long> resolve(List<DailyDiaryEmotion> streak, SessionTriggerContext context) {
        if (streak.isEmpty()) return List.of(context.voiceId());

        List<DailyDiaryEmotion> selected = switch (props.getContextWindow()) {
            case TRIGGER_ONLY -> streak.subList(0, 1);
            case FIXED_DAYS -> streak.subList(0, Math.min(props.getContextWindowDays(), streak.size()));
            case STREAK -> streak;
        };

        // 상한 적용 — 최신 쪽을 남긴다 (streak은 내림차순이므로 앞에서 자름)
        if (selected.size() > props.getMaxContextDiaries()) {
            selected = selected.subList(0, props.getMaxContextDiaries());
        }

        List<DailyDiaryEmotion> chronological = new ArrayList<>(selected);
        chronological.sort(Comparator.comparing(DailyDiaryEmotion::date));
        return chronological.stream().map(DailyDiaryEmotion::voiceId).toList();
    }
}
