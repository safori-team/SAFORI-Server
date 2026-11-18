package com.safori.api.operator.service;

import com.safori.api.chatbot.dto.MindDiaryOfferResponse;
import com.safori.api.operator.dto.OperatorMindDiaryStatusResponse;
import com.safori.api.operator.dto.TriggerMindDiaryOfferResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.entity.MindDiaryTriggerStatus;
import com.safori.domain.chatbot.policy.DailyDiaryEmotion;
import com.safori.domain.chatbot.policy.DiaryEmotionHistoryPort;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.policy.SessionTriggerDecision;
import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import com.safori.domain.chatbot.repository.MindDiaryTriggerRepository;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [개발/테스트 전용] 운영자가 특정 사용자의 마음일기 상담 조건을 확인하고, 스케줄러를 기다리지 않고
 * 바로 제안을 만든다.
 *
 * <p>판정은 스케줄러와 같은 {@link MindDiaryTriggerEvaluator}, 기록도 같은
 * {@link ChatbotDomainService#recordOffer} 를 쓴다 — 원장 멱등성과 제안 푸시(AFTER_COMMIT)가 그대로 적용된다.
 */
@UseCase
@RequiredArgsConstructor
public class OperatorMindDiaryUseCase {

    static final String NO_CANDIDATE = "NO_CANDIDATE";
    static final String ALREADY_TRIGGERED = "ALREADY_TRIGGERED";

    /** 한 번에 평가할 최신 미트리거 일기 수. 스케줄러가 놓친 과거 일기까지 훑을 필요는 없다. */
    private static final int MAX_CANDIDATES = 10;

    private final UserAdaptor userAdaptor;
    private final SessionTriggerProperties props;
    private final DiaryEmotionHistoryPort historyPort;
    private final MindDiaryTriggerEvaluator evaluator;
    private final MindDiaryTriggerRepository mindDiaryTriggerRepository;
    private final ChatbotDomainService chatbotDomainService;

    @Transactional(readOnly = true)
    public OperatorMindDiaryStatusResponse status(String username, int days) {
        User user = userAdaptor.queryUserByUsername(username);
        LocalDate today = LocalDate.now();
        List<DailyDiaryEmotion> rows = historyPort.findDailyEmotions(user.getId(), today.minusDays(days - 1L), today);

        Map<Long, MindDiaryTriggerStatus> offerStatusByVoice = mindDiaryTriggerRepository
                .findByVoice_IdIn(rows.stream().map(DailyDiaryEmotion::voiceId).toList()).stream()
                .collect(Collectors.toMap(t -> t.getVoice().getId(), MindDiaryTrigger::getStatus));

        Set<EmotionType> negatives = props.getNegativeEmotions();
        List<OperatorMindDiaryStatusResponse.Diary> diaries = rows.stream()
                .map(row -> new OperatorMindDiaryStatusResponse.Diary(
                        row.voiceId(), row.date(), row.emotion(), negatives.contains(row.emotion()),
                        offerStatusByVoice.get(row.voiceId())))
                .toList();

        return new OperatorMindDiaryStatusResponse(
                user.getUsername(),
                props.getConsecutiveDays(),
                negatives.stream().sorted().toList(),
                negativeStreakEndingToday(rows, today, negatives),
                diaries,
                pendingOffer(user));
    }

    /**
     * 최신 미트리거 일기부터 평가해 처음 조건을 채우는 일기 하나로 제안을 만든다.
     * 트랜잭션을 걸지 않는다 — {@code recordOffer} 가 자기 트랜잭션으로 커밋해야 제안 푸시가 나가고,
     * 원장 UNIQUE 위반(스케줄러가 먼저 제안)도 그 트랜잭션만 롤백된다.
     */
    public TriggerMindDiaryOfferResponse triggerOffer(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        List<Long> candidates = historyPort.findUntriggeredVoiceIdsOfUser(user.getId(), MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return new TriggerMindDiaryOfferResponse(false, null, NO_CANDIDATE, pendingOffer(user));
        }

        String latestSkipReason = null;
        for (Long voiceId : candidates) {
            MindDiaryTriggerEvaluator.Evaluation eval = evaluator.evaluate(voiceId);
            SessionTriggerDecision decision = eval.decision();
            if (!decision.create()) {
                if (latestSkipReason == null) latestSkipReason = decision.reasonCode();
                continue;
            }
            try {
                chatbotDomainService.recordOffer(eval.voice(), decision.reasonCode());
            } catch (DataIntegrityViolationException dup) {
                // 스케줄러가 같은 일기로 먼저 제안했다. 다음 후보로 넘어간다.
                continue;
            }
            return new TriggerMindDiaryOfferResponse(true, voiceId, decision.reasonCode(), pendingOffer(user));
        }

        String reason = latestSkipReason == null ? ALREADY_TRIGGERED : latestSkipReason;
        return new TriggerMindDiaryOfferResponse(false, candidates.get(0), reason, pendingOffer(user));
    }

    /** 정책과 같은 방식으로 센다: 날짜별 최신 일기 하나만 보고, 하루라도 비거나 부정이 아니면 끊긴다. */
    private int negativeStreakEndingToday(List<DailyDiaryEmotion> rows, LocalDate today, Set<EmotionType> negatives) {
        Map<LocalDate, DailyDiaryEmotion> byDate = new HashMap<>();
        // rows 는 최신순 — 같은 날짜가 둘 이상이면(시딩은 1일 1일기 제약을 건너뜀) 최신 것만 남긴다
        rows.forEach(row -> byDate.putIfAbsent(row.date(), row));

        int streak = 0;
        while (true) {
            DailyDiaryEmotion day = byDate.get(today.minusDays(streak));
            if (day == null || !negatives.contains(day.emotion())) return streak;
            streak++;
        }
    }

    private MindDiaryOfferResponse pendingOffer(User user) {
        return mindDiaryTriggerRepository
                .findByVoice_User_IdAndStatusOrderByCreatedDateDesc(user.getId(), MindDiaryTriggerStatus.OFFERED)
                .stream()
                .findFirst()
                .map(offer -> MindDiaryOfferResponse.of(offer.getId(), offer.getReason(), offer.getCreatedDate()))
                .orElseGet(MindDiaryOfferResponse::none);
    }
}
