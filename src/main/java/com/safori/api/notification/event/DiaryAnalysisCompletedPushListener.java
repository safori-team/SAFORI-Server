package com.safori.api.notification.event;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.service.SendPushNotificationUseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 마음일기 감정 분석이 끝나면 소유자에게 "분석 완료" 푸시를 보낸다.
 *
 * <p>분석은 STT+감정+소분류 파이프라인으로 1분 이상 걸릴 수 있어, 완료 시점에 알림이 필요하다.
 * {@link VoiceAnalysisCompletedEvent}는 <b>마음일기 최초 분석 성공 시에만</b> 발행되므로(재분석·채팅
 * 음성 경로는 발행하지 않음) 이 리스너는 자연히 마음일기에만 적용된다.
 *
 * <p>소분류 분석을 외부 파이프라인에 넘긴 경우 이 이벤트는 응답을 받은 뒤(또는 타임아웃 마감 시)
 * 발행된다. 같은 요청이 중복 전달돼도 원장이 최종 상태를 한 번만 확정하므로 푸시도 한 번만 나간다.
 *
 * <p>이벤트는 분석 스레드에서 저장 커밋 이후(트랜잭션 밖) 발행되므로 평범한 {@code @EventListener}로
 * 받는다. {@code @Async}로 전송을 분리하고, 실패는 삼켜(로그만) 분석 흐름에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryAnalysisCompletedPushListener {

    private final VoiceAdaptor voiceAdaptor;
    private final SendPushNotificationUseCase sendPushNotificationUseCase;

    @Async
    @EventListener
    public void onDiaryAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        try {
            Voice voice = voiceAdaptor.queryById(event.voiceId());
            Long userId = voice.getUser().getId();

            PushMessage message = new PushMessage(
                    NotificationStaticValues.DIARY_ANALYSIS_DONE_TITLE,
                    NotificationStaticValues.DIARY_ANALYSIS_DONE_BODY,
                    Map.of(
                            NotificationStaticValues.DATA_TYPE, NotificationStaticValues.TYPE_DIARY_ANALYSIS_DONE,
                            NotificationStaticValues.KEY_VOICE_ID, String.valueOf(event.voiceId())
                    ));
            sendPushNotificationUseCase.execute(userId, message);
        } catch (Exception e) {
            log.error("마음일기 분석 완료 푸시 실패 (silent) — voiceId={}", event.voiceId(), e);
        }
    }
}
