package com.safori.api.notification.scheduler;

import com.safori.api.notification.service.SendCarePushUseCase;
import com.safori.domain.notification.entity.CareNotificationType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주의 기록이 48시간 동안 미확인이면 담당자·보호자에게 푸시를 보낸다.
 *
 * <p>10분마다 "현재 기록이 주의·미확인이고 48시간이 지났으며 아직 안 보낸" 기록을 찾는다. 상태가 DB에 있어
 * 재배포해도 다음 주기에 그대로 잡힌다. 컨테이너가 여러 개여도 ShedLock으로 한 곳에서만 돌고,
 * 겹치더라도 발송 이력의 유니크 제약이 두 번 보내지 않게 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CautionUncheckedPushScheduler {

    private final SendCarePushUseCase sendCarePushUseCase;

    @SchedulerLock(name = "cautionUncheckedPush", lockAtMostFor = "PT10M")
    @Scheduled(initialDelayString = "PT2M",
            fixedDelayString = "${safori.notification.caution-unchecked-interval:PT10M}")
    public void run() {
        for (Long recordId : sendCarePushUseCase.cautionUncheckedRecordIds(LocalDateTime.now())) {
            try {
                sendCarePushUseCase.execute(recordId, CareNotificationType.CAUTION_UNCHECKED_48H);
            } catch (Exception e) {
                // 한 건 실패해도 나머지는 계속. 이력이 안 남았으면 다음 주기에 다시 잡힌다.
                log.error("주의 48시간 미확인 푸시 실패 — recordId={}", recordId, e);
            }
        }
    }
}
