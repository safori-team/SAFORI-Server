package com.safori.api.voice.service;

import com.safori.api.emotion.service.GetWeeklyEmotionReportUseCase;
import com.safori.common.annotation.UseCase;
import com.safori.common.util.DateRangeUtil;
import com.safori.api.common.dto.WeekDay;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.api.emotion.dto.WeekDayEmotion;
import com.safori.api.emotion.dto.WeeklyAnalysisCombinedResponse;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetWeeklyEmotionAnalysisUseCase {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GetWeeklyEmotionReportUseCase getWeeklyEmotionReportUseCase;

    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * @param yearMonth "yyyy-MM" 형식
     * @param week      1-indexed 주차 (1 = 첫째 주)
     */
    public WeeklyAnalysisCombinedResponse execute(String username, String yearMonth, int week) {
        DateRangeUtil.DateRange range = DateRangeUtil.calendarWeekRange(yearMonth, week);
        List<VoiceComposite> composites = voiceCompositeAdaptor.queryByUsernameAndDateRange(
                username, range.getStart(), range.getEnd());
        List<WeekDayEmotion> weeklyEmotions = buildWeeklyEmotionsFromComposites(range, composites);

        String reportMessage = getWeeklyEmotionReportUseCase.execute(
                username, yearMonth, week, weeklyEmotions, composites);

        return WeeklyAnalysisCombinedResponse.builder()
                .weeklyEmotions(weeklyEmotions)
                .reportMessage(reportMessage)
                .build();
    }

    /**
     * 홈화면용 — 이번 주(일~토)의 일별 감정 목록을 AI 리포트 없이 반환.
     * yearMonth / week 를 서버에서 자동 계산한다.
     */
    public List<WeekDayEmotion> executeCurrentWeek(String username) {
        LocalDate today = LocalDate.now();
        String yearMonth = today.format(YEAR_MONTH_FMT);
        int week = resolveCalendarWeek(today);
        DateRangeUtil.DateRange range = DateRangeUtil.calendarWeekRange(yearMonth, week);
        List<VoiceComposite> composites = voiceCompositeAdaptor.queryByUsernameAndDateRange(
                username, range.getStart(), range.getEnd());
        return buildWeeklyEmotionsFromComposites(range, composites);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * range + composites 를 받아 일별 WeekDayEmotion 리스트를 구성.
     * execute() 와 executeCurrentWeek() 가 공유한다.
     * 1일 1마음일기 제약으로 하루당 최대 1건이므로, 방어적으로 최신 1건을 선택해
     * 그 일기의 대표 감정과 voiceId 를 매칭한다.
     */
    private List<WeekDayEmotion> buildWeeklyEmotionsFromComposites(
            DateRangeUtil.DateRange range, List<VoiceComposite> composites) {
        LocalDate weekStart = range.getStart().toLocalDate();
        LocalDate rangeEndExclusive = range.getEnd().toLocalDate();
        LocalDate endDateExclusive = weekStart.plusDays(7).isBefore(rangeEndExclusive)
                ? weekStart.plusDays(7)
                : rangeEndExclusive;

        Map<LocalDate, List<VoiceComposite>> byDate = composites.stream()
                .collect(Collectors.groupingBy(vc -> vc.getCreatedDate().toLocalDate()));

        List<WeekDayEmotion> result = new ArrayList<>();
        for (LocalDate date = weekStart; date.isBefore(endDateExclusive); date = date.plusDays(1)) {
            VoiceComposite daily = byDate.getOrDefault(date, List.of()).stream()
                    .max(Comparator
                            .comparing(VoiceComposite::getCreatedDate)
                            .thenComparing(VoiceComposite::getId))
                    .orElse(null);

            result.add(WeekDayEmotion.builder()
                    .date(date)
                    .weekDay(toWeekDay(date.getDayOfWeek()))
                    .emotionType(daily != null ? daily.getTopEmotion() : null)
                    .voiceId(daily != null ? daily.getVoice().getId() : null)
                    .build());
        }
        return result;
    }

    /**
     * 오늘 날짜가 해당 월의 몇 번째 주(캘린더 행 기준, 일요일 시작)인지 계산.
     * DateRangeUtil.calendarWeekRange 의 week 파라미터와 동일한 기준.
     */
    private static int resolveCalendarWeek(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        LocalDate firstOfMonth = ym.atDay(1);
        int daysFromSunday = firstOfMonth.getDayOfWeek() == DayOfWeek.SUNDAY
                ? 0
                : firstOfMonth.getDayOfWeek().getValue(); // MON=1 … SAT=6
        LocalDate week1Start = firstOfMonth.minusDays(daysFromSunday);
        return (int) ChronoUnit.WEEKS.between(week1Start, date) + 1;
    }

    private WeekDay toWeekDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> WeekDay.MON;
            case TUESDAY -> WeekDay.TUE;
            case WEDNESDAY -> WeekDay.WED;
            case THURSDAY -> WeekDay.THU;
            case FRIDAY -> WeekDay.FRI;
            case SATURDAY -> WeekDay.SAT;
            case SUNDAY -> WeekDay.SUN;
        };
    }
}
