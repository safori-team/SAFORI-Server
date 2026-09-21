package com.safori.api.emotion.service;

import com.safori.common.annotation.UseCase;
import com.safori.api.emotion.dto.WeekDayEmotion;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.WeeklyEmotionReport;
import com.safori.domain.voice.repository.WeeklyEmotionReportRepository;
import com.safori.infra.openai.OpenAiWeeklyReportClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@UseCase
@Transactional
@RequiredArgsConstructor
public class GetWeeklyEmotionReportUseCase {

    private static final String NO_DATA_MESSAGE = "해당 주에는 감정분석 데이터가 없었습니다.";
    private static final String REPORT_UNAVAILABLE_MESSAGE = "이번 주 감정 리포트를 준비하지 못했습니다. 잠시 후 다시 확인해주세요.";

    private final UserAdaptor userAdaptor;
    private final WeeklyEmotionReportRepository weeklyEmotionReportRepository;
    private final OpenAiWeeklyReportClient openAiWeeklyReportClient;

    public String execute(String username, String month, int week, List<WeekDayEmotion> weeklyEmotions, List<VoiceComposite> composites) {
        User user = userAdaptor.queryUserByUsername(username);
        // 가장 최근에 업데이트 된 주간 데이터 있는지 확인
        Long latestVoiceCompositeId = composites.stream()
                .max(Comparator
                        .comparing(VoiceComposite::getCreatedDate)
                        .thenComparing(VoiceComposite::getId))
                .map(VoiceComposite::getId)
                .orElse(null);

        // 기존에 리포트 작성한 데이터 불러오기(업데이트 시간 비교용)
        WeeklyEmotionReport cached = weeklyEmotionReportRepository
                .findByUser_IdAndReportMonthAndReportWeek(user.getId(), month, week)
                .orElse(null);

        if (latestVoiceCompositeId == null) {
            if (cached != null && cached.getLatestVoiceCompositeId() == null) {
                return cached.getReportMessage();
            }
            return saveOrUpdate(cached, user, month, week, null, NO_DATA_MESSAGE).getReportMessage();
        }

        if (cached != null && Objects.equals(cached.getLatestVoiceCompositeId(), latestVoiceCompositeId)) {
            return cached.getReportMessage();
        }

        // AI 리포트 생성은 부가 정보다. OpenAI 미설정/장애로 실패해도 주간 감정 데이터 응답을
        // 막지 않는다(과거엔 예외가 그대로 전파돼 데이터가 있는 주차 전체가 500이 났다).
        // 실패분은 캐시에 저장하지 않아, 다음 조회 때 정상화되면 자동으로 다시 시도된다.
        String reportMessage;
        try {
            reportMessage = openAiWeeklyReportClient.generateWeeklyReport(
                    UserHonorific.displayName(user), user.getGender(), weeklyEmotions);
        } catch (Exception e) {
            log.warn("[WeeklyReport] OpenAI 리포트 생성 실패 - fallback 응답. userId={}, month={}, week={}",
                    user.getId(), month, week, e);
            return cached != null ? cached.getReportMessage() : REPORT_UNAVAILABLE_MESSAGE;
        }
        return saveOrUpdate(cached, user, month, week, latestVoiceCompositeId, reportMessage).getReportMessage();
    }

    private WeeklyEmotionReport saveOrUpdate(WeeklyEmotionReport cached,
                                             User user,
                                             String month,
                                             int week,
                                             Long latestVoiceCompositeId,
                                             String reportMessage) {
        if (cached != null) {
            cached.update(latestVoiceCompositeId, reportMessage);
            return cached;
        }

        WeeklyEmotionReport created = WeeklyEmotionReport.builder()
                .user(user)
                .reportMonth(month)
                .reportWeek(week)
                .latestVoiceCompositeId(latestVoiceCompositeId)
                .reportMessage(reportMessage)
                .build();
        return weeklyEmotionReportRepository.save(created);
    }
}
