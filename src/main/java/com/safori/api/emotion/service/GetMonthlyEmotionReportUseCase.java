package com.safori.api.emotion.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.MonthlyEmotionReport;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.repository.MonthlyEmotionReportRepository;
import com.safori.infra.openai.OpenAiMonthlyReportClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@UseCase
@Transactional
@RequiredArgsConstructor
public class GetMonthlyEmotionReportUseCase {

    private static final String NO_DATA_MESSAGE = "해당 달에는 감정 분석 데이터가 없었습니다.";
    private static final String REPORT_UNAVAILABLE_MESSAGE = "이번 달 감정 리포트를 준비하지 못했습니다. 잠시 후 다시 확인해주세요.";

    private final UserAdaptor userAdaptor;
    private final MonthlyEmotionReportRepository monthlyEmotionReportRepository;
    private final OpenAiMonthlyReportClient openAiMonthlyReportClient;

    public String execute(String username, String month, Map<EmotionType, Long> emotionCounts, List<VoiceComposite> composites) {
        User user = userAdaptor.queryUserByUsername(username);
        Long latestVoiceCompositeId = composites.stream()
                .max(Comparator
                        .comparing(VoiceComposite::getCreatedDate)
                        .thenComparing(VoiceComposite::getId))
                .map(VoiceComposite::getId)
                .orElse(null);

        MonthlyEmotionReport cached = monthlyEmotionReportRepository
                .findByUser_IdAndReportMonth(user.getId(), month)
                .orElse(null);

        if (latestVoiceCompositeId == null) {
            if (cached != null && cached.getLatestVoiceCompositeId() == null) {
                return cached.getReportMessage();
            }
            return saveOrUpdate(cached, user, month, null, NO_DATA_MESSAGE).getReportMessage();
        }

        if (cached != null && Objects.equals(cached.getLatestVoiceCompositeId(), latestVoiceCompositeId)) {
            return cached.getReportMessage();
        }

        // AI 리포트 생성은 부가 정보다. OpenAI 미설정/장애로 실패해도 월간 감정 집계 응답을
        // 막지 않는다(과거엔 예외가 그대로 전파돼 데이터가 있는 달 전체가 500이 났다).
        // 실패분은 캐시에 저장하지 않아, 다음 조회 때 정상화되면 자동으로 다시 시도된다.
        String reportMessage;
        try {
            reportMessage = openAiMonthlyReportClient.generateMonthlyReport(
                    UserHonorific.displayName(user), user.getGender(), emotionCounts);
        } catch (Exception e) {
            log.warn("[MonthlyReport] OpenAI 리포트 생성 실패 - fallback 응답. userId={}, month={}",
                    user.getId(), month, e);
            return cached != null ? cached.getReportMessage() : REPORT_UNAVAILABLE_MESSAGE;
        }
        return saveOrUpdate(cached, user, month, latestVoiceCompositeId, reportMessage).getReportMessage();
    }

    private MonthlyEmotionReport saveOrUpdate(MonthlyEmotionReport cached,
                                              User user,
                                              String month,
                                              Long latestVoiceCompositeId,
                                              String reportMessage) {
        if (cached != null) {
            cached.update(latestVoiceCompositeId, reportMessage);
            return cached;
        }

        MonthlyEmotionReport created = MonthlyEmotionReport.builder()
                .user(user)
                .reportMonth(month)
                .latestVoiceCompositeId(latestVoiceCompositeId)
                .reportMessage(reportMessage)
                .build();
        return monthlyEmotionReportRepository.save(created);
    }
}
