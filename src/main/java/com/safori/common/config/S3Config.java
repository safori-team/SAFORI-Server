package com.safori.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 수동 설정.
 * AWS_S3_BUCKET 이 비어있으면 빈이 생성되지 않아 서버가 정상 기동된다.
 *
 * <p>자격증명은 정적 키가 있으면 그것을, 없으면 기본 체인(인스턴스 프로파일·표준 env·CLI
 * 프로파일)을 쓴다. 서버에서는 EC2 instance role 을 쓰므로 키를 두지 않는다.
 */
@Slf4j
@Configuration
@ConditionalOnExpression("!'${spring.cloud.aws.s3.bucket:}'.isEmpty()")
public class S3Config {

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .credentialsProvider(credentialsProvider())
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(credentialsProvider())
                .region(Region.of(region))
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey != null && !accessKey.isBlank()) {
            log.info("S3 클라이언트 — 정적 자격증명 사용, region={}", region);
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        log.info("S3 클라이언트 — 기본 자격증명 체인 사용, region={}", region);
        return DefaultCredentialsProvider.create();
    }
}
