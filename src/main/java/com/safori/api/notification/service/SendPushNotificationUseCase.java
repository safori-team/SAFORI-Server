package com.safori.api.notification.service;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushNotificationSender;
import com.safori.api.notification.port.PushSendResult;
import com.safori.common.annotation.UseCase;
import com.safori.domain.notification.adaptor.DeviceTokenAdaptor;
import com.safori.domain.notification.entity.DeviceToken;
import com.safori.domain.notification.service.DeviceTokenDomainService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자의 모든 디바이스로 푸시를 전송하는 UseCase.
 * 토큰 조회(Adaptor) → 전송(Port) → 무효 토큰 정리(DomainService) 를 조합한다.
 * #116 알림 이벤트 리스너의 진입점.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendPushNotificationUseCase {

    private final DeviceTokenAdaptor deviceTokenAdaptor;
    private final DeviceTokenDomainService deviceTokenDomainService;
    private final PushNotificationSender pushNotificationSender;

    public PushSendResult execute(Long userId, PushMessage message) {
        List<String> tokens = deviceTokenAdaptor.queryTokensByUserId(userId).stream()
                .map(DeviceToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            return PushSendResult.empty();
        }

        PushSendResult result = pushNotificationSender.send(tokens, message);

        if (!result.invalidTokens().isEmpty()) {
            deviceTokenDomainService.deleteByTokens(result.invalidTokens());
            log.info("무효 디바이스 토큰 {}건 삭제 (userId={})", result.invalidTokens().size(), userId);
        }
        return result;
    }
}
