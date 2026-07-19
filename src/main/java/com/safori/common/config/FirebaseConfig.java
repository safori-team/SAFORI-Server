package com.safori.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase Admin SDK 수동 설정 (FCM 푸시 알림).
 * FIREBASE_CREDENTIALS_BASE64 가 비어있으면 빈이 생성되지 않아 서버가 정상 기동된다.
 * (서비스 계정 키 JSON 을 base64 인코딩한 값 — 파일 마운트 없이 환경변수만으로 주입)
 */
@Configuration
public class FirebaseConfig {

    @Bean
    @ConditionalOnExpression("!'${firebase.credentials-base64:}'.isEmpty()")
    public FirebaseApp firebaseApp(
            @Value("${firebase.credentials-base64}") String credentialsBase64) throws IOException {
        byte[] serviceAccountJson = Base64.getDecoder().decode(credentialsBase64);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson)))
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
