package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.chatbot.policy.DailyDiaryEmotion;
import com.safori.domain.chatbot.policy.DiaryEmotionHistoryPort;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.voice.repository.DiaryEmotionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DiaryEmotionHistoryPort} 구현. voice 도메인이 자기 데이터 조회를 책임진다.
 */
@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiaryEmotionHistoryAdaptorImpl implements DiaryEmotionHistoryPort {

    private final DiaryEmotionHistoryRepository repository;

    @Override
    public List<Long> findUntriggeredVoiceIds(LocalDateTime since, int limit) {
        return repository.findUntriggeredVoiceIds(since, PageRequest.of(0, limit));
    }

    @Override
    public List<DailyDiaryEmotion> findDailyEmotions(Long userId, LocalDate from, LocalDate to) {
        List<Object[]> rows = repository.findEmotionRows(
                userId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        List<DailyDiaryEmotion> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long voiceId = (Long) row[0];
            LocalDateTime createdDate = (LocalDateTime) row[1];
            EmotionType aiTopEmotion = (EmotionType) row[2];
            EmotionType reportedEmotion = (EmotionType) row[3];
            result.add(new DailyDiaryEmotion(
                    createdDate.toLocalDate(),
                    voiceId,
                    EmotionResolver.effectiveTopEmotion(aiTopEmotion, reportedEmotion)));
        }
        return result;
    }
}
