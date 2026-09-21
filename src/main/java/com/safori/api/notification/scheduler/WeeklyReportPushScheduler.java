package com.safori.api.notification.scheduler;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.service.SendPushNotificationUseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.common.util.DateRangeUtil;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지난 한 주(일~토)에 마음일기를 남긴 사용자에게 "주간 리포트 도착" 푸시를 보낸다.
 *
 * <p>매주 월요일 아침(기본 09:00 KST)에 1회 실행. 리포트 본문은 미리 만들지 않는다(lazy) —
 * 사용자가 알림을 눌러 주간 리포트 화면을 열면 기존 {@code GET /weekly} 경로가 캐시/생성한다.
 * 그 주 일기가 확정돼 더 늘지 않으므로 첫 조회 1회만 OpenAI를 호출하고 이후 캐시된다.
 *
 * <p>딥링크 페이로드({@code type/yearMonth/week})로 알림 탭 시 정확히 지난주 리포트 화면으로
 * 이동한다. 주가 두 달에 걸치면 캘린더 뷰 규칙대로 주 시작(일요일)이 속한 달로 귀속된다.
 *
 * <p><b>다중 인스턴스 주의:</b> 인스턴스를 2대 이상으로 늘리면 각 인스턴스가 중복 발송할 수 있다.
 * 그때는 {@code MindDiarySessionScheduler} 와 동일하게 ShedLock 도입을 검토한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportPushScheduler {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final SendPushNotificationUseCase sendPushNotificationUseCase;

    @Scheduled(
            cron = "${safori.notification.weekly-report-cron:0 0 9 * * MON}",
            zone = "${safori.notification.timezone:Asia/Seoul}")
    public void run() {
        LocalDate today = LocalDate.now();
        LocalDate prevWeekSunday = previousWeekSunday(today);
        LocalDateTime start = prevWeekSunday.atStartOfDay();
        LocalDateTime end = prevWeekSunday.plusDays(7).atStartOfDay(); // exclusive (이번 주 일요일 00:00)

        DateRangeUtil.WeekOfMonth week = DateRangeUtil.weekOf(prevWeekSunday);

        List<Long> userIds = voiceCompositeAdaptor.queryDistinctUserIdsByDateRange(start, end);
        if (userIds.isEmpty()) {
            log.info("주간 리포트 푸시 — 대상 없음. 기간=[{} ~ {})", start, end);
            return;
        }

        PushMessage message = new PushMessage(
                NotificationStaticValues.WEEKLY_REPORT_TITLE,
                NotificationStaticValues.WEEKLY_REPORT_BODY,
                Map.of(
                        NotificationStaticValues.DATA_TYPE, NotificationStaticValues.TYPE_WEEKLY_REPORT,
                        NotificationStaticValues.KEY_YEAR_MONTH, week.yearMonth(),
                        NotificationStaticValues.KEY_WEEK, String.valueOf(week.week())
                ));

        log.info("주간 리포트 푸시 시작 — 대상 {}명, {}주차({})", userIds.size(), week.week(), week.yearMonth());
        int sent = 0;
        for (Long userId : userIds) {
            try {
                sendPushNotificationUseCase.execute(userId, message);
                sent++;
            } catch (Exception e) {
                // 한 명 실패해도 나머지는 계속. 다음 주에 재시도.
                log.error("주간 리포트 푸시 실패 — userId={}", userId, e);
            }
        }
        log.info("주간 리포트 푸시 완료 — 대상 {}명 중 {}명 발송", userIds.size(), sent);
    }

    /** 오늘 기준 직전 캘린더 주(일요일 시작)의 일요일. */
    private static LocalDate previousWeekSunday(LocalDate today) {
        int daysFromSunday = today.getDayOfWeek() == DayOfWeek.SUNDAY
                ? 0
                : today.getDayOfWeek().getValue(); // MON=1 ... SAT=6
        LocalDate thisWeekSunday = today.minusDays(daysFromSunday);
        return thisWeekSunday.minusWeeks(1);
    }
}
