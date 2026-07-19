package com.safori.infra.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushSendResult;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * FCM 실발송 수동 검증. Spring 컨텍스트/DB 없이 Firebase 만 직접 초기화한다.
 * 환경변수가 없으면 자동 skip 되므로 CI(`./gradlew test`)에는 영향 없다.
 *
 * <p>실행 예:
 * <pre>
 * # (1) 자격증명만 검증 — 디바이스 토큰 불필요, 키가 Google 인증 되는지 확인
 * FIREBASE_CREDENTIALS_BASE64="$(base64 -i serviceAccountKey.json | tr -d '\n')" \
 *   ./gradlew test --tests '*FcmManualVerifyTest.credentials*' -i
 *
 * # (2) 실기기 전송 — 실제 디바이스 registration token 필요
 * FIREBASE_CREDENTIALS_BASE64="$(base64 -i serviceAccountKey.json | tr -d '\n')" \
 * TEST_FCM_TOKEN="&lt;클라이언트에서 받은 FCM 토큰&gt;" \
 *   ./gradlew test --tests '*FcmManualVerifyTest.realSend*' -i
 * </pre>
 */
class FcmManualVerifyTest {

    private static final String FIREBASE_MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private FirebaseApp firebaseApp;

    @AfterEach
    void tearDown() {
        if (firebaseApp != null) {
            firebaseApp.delete();
        }
    }

    @Test
    @DisplayName("자격증명 검증 - 서비스 계정 키가 Google 액세스 토큰을 발급받는다 (디바이스 토큰 불필요)")
    @EnabledIfEnvironmentVariable(named = "FIREBASE_CREDENTIALS_BASE64", matches = ".+")
    void credentials_areValid() throws Exception {
        byte[] json = Base64.getDecoder().decode(System.getenv("FIREBASE_CREDENTIALS_BASE64"));
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(json))
                .createScoped(List.of(FIREBASE_MESSAGING_SCOPE));

        AccessToken token = credentials.refreshAccessToken();

        // 키가 유효하고 Google 인증에 성공하면 액세스 토큰이 발급된다 → 발송 파이프라인 자격증명 OK
        assertThat(token.getTokenValue()).isNotBlank();
    }

    @Test
    @DisplayName("실발송 - FcmPushNotificationSender 로 실제 디바이스 토큰에 푸시를 보낸다")
    @EnabledIfEnvironmentVariable(named = "FIREBASE_CREDENTIALS_BASE64", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "TEST_FCM_TOKEN", matches = ".+")
    void realSend_deliversToDevice() throws Exception {
        FirebaseMessaging messaging = initFirebaseMessaging();
        String token = System.getenv("TEST_FCM_TOKEN");

        ObjectProvider<FirebaseMessaging> provider = asProvider(messaging);
        FcmPushNotificationSender sender = new FcmPushNotificationSender(provider);

        PushSendResult result = sender.send(
                List.of(token),
                PushMessage.of("SAFORI 테스트", "FCM 발송 검증 메시지"));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
        assertThat(result.invalidTokens()).isEmpty();
    }

    private FirebaseMessaging initFirebaseMessaging() throws Exception {
        byte[] json = Base64.getDecoder().decode(System.getenv("FIREBASE_CREDENTIALS_BASE64"));
        firebaseApp = FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)))
                        .build(),
                "fcm-manual-verify");
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FirebaseMessaging> asProvider(FirebaseMessaging messaging) {
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(messaging);
        return provider;
    }
}
