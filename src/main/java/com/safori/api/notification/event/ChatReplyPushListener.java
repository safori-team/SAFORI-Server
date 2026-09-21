package com.safori.api.notification.event;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.service.SendPushNotificationUseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.common.event.ChatReplySettledEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도란이 응답이 확정되면 사용자에게 푸시를 보낸다.
 *
 * <p>응답을 기다리다 앱을 나간 사용자가 결과를 놓치지 않도록 하는 게 목적이다. 소요 시간
 * 게이트는 두지 않아 매 턴 발송되므로, <b>포그라운드 표시 억제는 앱이 책임진다.</b>
 *
 * <p>{@code AFTER_COMMIT}으로 소비하므로 상태 갱신이 롤백되면 푸시가 나가지 않는다.
 * 전송 실패는 삼키고(로그만) 대화 흐름에 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReplyPushListener {

    private final SendPushNotificationUseCase sendPushNotificationUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatReplySettled(ChatReplySettledEvent event) {
        try {
            sendPushNotificationUseCase.execute(event.userId(), toMessage(event));
        } catch (Exception e) {
            log.error("도란이 응답 푸시 실패 (silent) — userId={}, messageId={}",
                    event.userId(), event.messageId(), e);
        }
    }

    private PushMessage toMessage(ChatReplySettledEvent event) {
        boolean failed = event.failed();
        return new PushMessage(
                failed ? NotificationStaticValues.CHAT_REPLY_FAILED_TITLE
                       : NotificationStaticValues.CHAT_REPLY_DONE_TITLE,
                failed ? NotificationStaticValues.CHAT_REPLY_FAILED_BODY
                       : NotificationStaticValues.CHAT_REPLY_DONE_BODY,
                Map.of(
                        NotificationStaticValues.DATA_TYPE,
                        failed ? NotificationStaticValues.TYPE_CHAT_REPLY_FAILED
                               : NotificationStaticValues.TYPE_CHAT_REPLY_DONE,
                        NotificationStaticValues.KEY_SESSION_ID, event.sessionId(),
                        NotificationStaticValues.KEY_MESSAGE_ID, String.valueOf(event.messageId())
                ));
    }
}
