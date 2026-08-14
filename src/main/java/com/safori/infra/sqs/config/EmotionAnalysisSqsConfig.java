package com.safori.infra.sqs.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * 소분류 감정 분석용 SQS 클라이언트 수동 설정.
 *
 * <p>큐 URL이 하나도 없으면 빈이 생성되지 않아 파이프라인이 비활성 상태로 정상 기동한다
 * (S3·Gemini·Firebase와 같은 패턴).
 *
 * <p>자격증명은 정적 키가 있으면 그것을, 없으면 기본 체인(인스턴스 프로파일·표준 env·CLI
 * 프로파일)을 쓴다. 애플리케이션 코드와 yaml에는 키를 두지 않는다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(EmotionAnalysisSqsProperties.class)
public class EmotionAnalysisSqsConfig {

    @Bean
    @ConditionalOnExpression(
            "!'${safori.emotion-analysis.request-queue-url:}'.isEmpty()"
                    + " or !'${safori.emotion-analysis.response-queue-url:}'.isEmpty()")
    public SqsClient emotionAnalysisSqsClient(
            @Value("${spring.cloud.aws.region.static:ap-northeast-2}") String region,
            @Value("${spring.cloud.aws.credentials.access-key:}") String accessKey,
            @Value("${spring.cloud.aws.credentials.secret-key:}") String secretKey) {

        AwsCredentialsProvider credentialsProvider;
        if (accessKey != null && !accessKey.isBlank()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
            log.info("감정 분석 SQS 클라이언트 — 정적 자격증명 사용, region={}", region);
        } else {
            credentialsProvider = DefaultCredentialsProvider.create();
            log.info("감정 분석 SQS 클라이언트 — 기본 자격증명 체인 사용, region={}", region);
        }

        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
