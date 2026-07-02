package com.safori.common.util;

import com.safori.common.exception.ErrorStatus;
import com.safori.common.exception.GeneralException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class DateRangeUtil {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private DateRangeUtil() {
    }

    /**
     * 해당 월 전체 범위를 반환한다. end는 다음 달 1일 00:00(exclusive).
     */
    public static DateRange monthRange(String month) {
        YearMonth yearMonth = YearMonth.parse(month, YEAR_MONTH_FORMATTER);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay();
        return new DateRange(start, end);
    }

    /**
     * 캘린더 행(일요일 시작) 기준 주간 범위를 반환한다.
     * week=1 은 해당 월 1일이 속한 캘린더 행의 일요일 00:00 부터 시작한다.
     * 요청 월의 범위로 clamp 한다.
     */
    public static DateRange calendarWeekRange(String month, int week) {
        if (week <= 0) {
            throw new GeneralException(ErrorStatus.DATE_RANGE_INVALID_WEEK);
        }

        YearMonth yearMonth = YearMonth.parse(month, YEAR_MONTH_FORMATTER);
        LocalDate firstOfMonth = yearMonth.atDay(1);

        int daysFromSunday = firstOfMonth.getDayOfWeek() == DayOfWeek.SUNDAY
                ? 0
                : firstOfMonth.getDayOfWeek().getValue(); // MON=1 ... SAT=6

        LocalDate weekStart = firstOfMonth.minusDays(daysFromSunday).plusWeeks(week - 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        // 요청한 월 범위로 clamp.
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        LocalDate clampedStart = weekStart.isBefore(monthStart) ? monthStart : weekStart;
        LocalDate clampedEnd = weekEnd.isAfter(monthEnd) ? monthEnd : weekEnd;

        if (clampedStart.isAfter(clampedEnd)) {
            throw new GeneralException(ErrorStatus.DATE_RANGE_INVALID_CALENDAR_WEEK_RANGE);
        }

        LocalDateTime start = clampedStart.atStartOfDay();
        LocalDateTime end = clampedEnd.plusDays(1).atStartOfDay(); // exclusive end
        return new DateRange(start, end);
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static final class DateRange {
        private final LocalDateTime start;
        private final LocalDateTime end;
    }
}
