package com.safori.infra.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FcmPushNotificationSenderTest {

    @Mock ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    @Mock FirebaseMessaging firebaseMessaging;

    private static final PushMessage MESSAGE = PushMessage.of("제목", "내용");

    private FcmPushNotificationSender sender() {
        return new FcmPushNotificationSender(firebaseMessagingProvider);
    }

    @Test
    @DisplayName("전송 - FirebaseMessaging 미설정이면 전송 없이 빈 결과를 반환한다")
    void send_skipsWhenFirebaseDisabled() {
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(null);

        PushSendResult result = sender().send(List.of("token-1"), MESSAGE);

        assertThat(result).isEqualTo(PushSendResult.empty());
    }

    @Test
    @DisplayName("전송 - 성공/실패 건수를 집계하고 UNREGISTERED 토큰을 무효로 수집한다")
    void send_collectsInvalidTokens() throws Exception {
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);

        SendResponse success = mock(SendResponse.class);
        given(success.isSuccessful()).willReturn(true);

        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        given(unregistered.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
        SendResponse failure = mock(SendResponse.class);
        given(failure.isSuccessful()).willReturn(false);
        given(failure.getException()).willReturn(unregistered);

        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getSuccessCount()).willReturn(1);
        given(batchResponse.getFailureCount()).willReturn(1);
        given(batchResponse.getResponses()).willReturn(List.of(success, failure));
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(batchResponse);

        PushSendResult result = sender().send(List.of("token-1", "token-dead"), MESSAGE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).containsExactly("token-dead");
    }

    @Test
    @DisplayName("전송 - 일시 오류(UNAVAILABLE 등) 토큰은 무효로 수집하지 않는다")
    void send_keepsTransientFailureTokens() throws Exception {
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);

        FirebaseMessagingException unavailable = mock(FirebaseMessagingException.class);
        given(unavailable.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
        SendResponse failure = mock(SendResponse.class);
        given(failure.isSuccessful()).willReturn(false);
        given(failure.getException()).willReturn(unavailable);

        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getSuccessCount()).willReturn(0);
        given(batchResponse.getFailureCount()).willReturn(1);
        given(batchResponse.getResponses()).willReturn(List.of(failure));
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(batchResponse);

        PushSendResult result = sender().send(List.of("token-1"), MESSAGE);

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    @DisplayName("전송 - FirebaseMessagingException 발생 시 예외 전파 없이 실패로 집계한다")
    void send_countsExceptionAsFailure() throws Exception {
        given(firebaseMessagingProvider.getIfAvailable()).willReturn(firebaseMessaging);
        given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class)))
                .willThrow(mock(FirebaseMessagingException.class));

        PushSendResult result = sender().send(List.of("token-1", "token-2"), MESSAGE);

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(2);
        assertThat(result.invalidTokens()).isEmpty();
    }
}
