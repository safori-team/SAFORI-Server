package com.safori.domain.care.policy;

import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecipientStatus;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.service.CareRecordDomainService;
import com.safori.domain.chatbot.repository.ChatSessionRepository;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.voice.repository.DiaryEmotionHistoryRepository;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 단계별 확인 방식 판정. 어르신 데이터를 읽어 {@link CareStatusRules}로 판정하고, 조건을 만족하면 기록을 올린다.
 * 기관에 등록된(활성) 대상자의 어르신만 판정한다.
 *
 * <p>집계 범위는 최근 30일이면서 그 사유를 마지막으로 완료한 이후다(완료하면 집계 초기화).
 */
@Component
@RequiredArgsConstructor
public class CareStatusDetector {

    static final int WINDOW_DAYS = 30;
    static final int INTERVAL_WINDOW_DAYS = 60;

    private final CareRecipientRepository recipientRepository;
    private final CareRecordDomainService recordDomainService;
    private final DiaryEmotionHistoryRepository diaryEmotionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final VoiceRepository voiceRepository;

    /** 동일 감정 반복 — 일기 분석이 끝나거나 재분석됐을 때. */
    @Transactional
    public void evaluateSameEmotion(Long userId, LocalDateTime now) {
        recipientOf(userId).ifPresent(recipient -> {
            LocalDateTime since = since(recipient, CareReasonType.SAME_EMOTION_REPEAT, now);
            List<EmotionType> latest = diaryEmotionRepository.findEmotionRows(userId, since, now.plusSeconds(1))
                    .stream()
                    .limit(CareStatusRules.LATEST)
                    .map(row -> EmotionResolver.effectiveTopEmotion((EmotionType) row[2], (EmotionType) row[3]))
                    .toList();
            CareStatusRules.sameEmotion(latest).ifPresent(message ->
                    recordDomainService.raise(recipient, CareReasonType.SAME_EMOTION_REPEAT, message, now));
        });
    }

    /** 추가 상담 반복 이용 — 상담을 연장했을 때. */
    @Transactional
    public void evaluateCounselExtension(Long userId, LocalDateTime now) {
        recipientOf(userId).ifPresent(recipient -> {
            LocalDateTime since = since(recipient, CareReasonType.COUNSEL_EXTENSION_REPEAT, now);
            List<Boolean> latest = chatSessionRepository
                    .findTop3ByUser_IdAndCreatedDateGreaterThanEqualOrderByCreatedDateDesc(userId, since).stream()
                    .map(session -> session.getExtendedAt() != null)
                    .toList();
            CareStatusRules.counselExtension(latest).ifPresent(message ->
                    recordDomainService.raise(recipient, CareReasonType.COUNSEL_EXTENSION_REPEAT, message, now));
        });
    }

    /** 작성 주기 감소 — 매일 배치. 기준일은 마지막 일기일과 이 사유 마지막 완료일 중 늦은 날. */
    @Transactional
    public void evaluateDiaryInterval(CareRecipient recipient, LocalDateTime now) {
        List<LocalDate> dates = voiceRepository.findCreatedDates(recipient.getUserId(),
                        now.minusDays(INTERVAL_WINDOW_DAYS), PageRequest.of(0, CareStatusRules.INTERVAL_SAMPLE))
                .stream().map(LocalDateTime::toLocalDate).toList();
        if (dates.isEmpty()) {
            return;
        }
        LocalDate base = recordDomainService.lastCompletedAt(recipient, CareReasonType.DIARY_INTERVAL_INCREASE)
                .map(LocalDateTime::toLocalDate)
                .filter(completed -> completed.isAfter(dates.get(0)))
                .orElse(dates.get(0));
        CareStatusRules.diaryInterval(dates, base, now.toLocalDate()).ifPresent(message ->
                recordDomainService.raise(recipient, CareReasonType.DIARY_INTERVAL_INCREASE, message, now));
    }

    /** 판정 배치 대상. */
    @Transactional(readOnly = true)
    public List<CareRecipient> intervalTargets() {
        return recipientRepository.findAllByStatusAndUserIdIsNotNull(CareRecipientStatus.ACTIVE);
    }

    private Optional<CareRecipient> recipientOf(Long userId) {
        return recipientRepository.findByUserIdAndStatus(userId, CareRecipientStatus.ACTIVE);
    }

    private LocalDateTime since(CareRecipient recipient, CareReasonType reasonType, LocalDateTime now) {
        LocalDateTime window = now.minusDays(WINDOW_DAYS);
        return recordDomainService.lastCompletedAt(recipient, reasonType)
                .filter(completed -> completed.isAfter(window))
                .orElse(window);
    }
}
