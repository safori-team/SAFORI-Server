package com.safori.api.notification.service;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushNotificationSender;
import com.safori.api.notification.port.PushSendResult;
import com.safori.domain.notification.adaptor.DeviceTokenAdaptor;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.service.DeviceTokenDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SendPushNotificationUseCaseTest {

    @Mock DeviceTokenAdaptor deviceTokenAdaptor;
    @Mock DeviceTokenDomainService deviceTokenDomainService;
    @Mock PushNotificationSender pushNotificationSender;
    @InjectMocks SendPushNotificationUseCase sendPushNotificationUseCase;

    private static final PushMessage MESSAGE = PushMessage.of("제목", "내용");

    @Test
    @DisplayName("푸시 전송 - 사용자의 모든 토큰으로 전송한다")
    void execute_sendsToAllTokens() {
        given(deviceTokenAdaptor.queryTokensByUserId(1L)).willReturn(List.of(
                DeviceToken.builder().token("token-1").build(),
                DeviceToken.builder().token("token-2").build()));
        given(pushNotificationSender.send(List.of("token-1", "token-2"), MESSAGE))
                .willReturn(new PushSendResult(2, 0, List.of()));

        PushSendResult result = sendPushNotificationUseCase.execute(1L, MESSAGE);

        assertThat(result.successCount()).isEqualTo(2);
        then(deviceTokenDomainService).should(never()).deleteByTokens(anyList());
    }

    @Test
    @DisplayName("푸시 전송 - 등록된 토큰이 없으면 전송하지 않는다")
    void execute_skipsWhenNoTokens() {
        given(deviceTokenAdaptor.queryTokensByUserId(1L)).willReturn(List.of());

        PushSendResult result = sendPushNotificationUseCase.execute(1L, MESSAGE);

        assertThat(result).isEqualTo(PushSendResult.empty());
        then(pushNotificationSender).should(never()).send(anyList(), any());
    }

    @Test
    @DisplayName("푸시 전송 - 무효 토큰은 전송 후 도메인 서비스로 삭제 위임한다")
    void execute_deletesInvalidTokens() {
        given(deviceTokenAdaptor.queryTokensByUserId(1L)).willReturn(List.of(
                DeviceToken.builder().token("token-1").build(),
                DeviceToken.builder().token("token-dead").build()));
        given(pushNotificationSender.send(anyList(), any()))
                .willReturn(new PushSendResult(1, 1, List.of("token-dead")));

        sendPushNotificationUseCase.execute(1L, MESSAGE);

        then(deviceTokenDomainService).should().deleteByTokens(List.of("token-dead"));
    }
}
