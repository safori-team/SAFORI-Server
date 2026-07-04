package com.safori.common.config;

import io.sentry.opentelemetry.otlp.OpenTelemetryOtlpEventProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentry 에러 이벤트에 OTel trace/span id 를 붙여 트레이스와 연결한다. DSN 비면 미생성.
 */
@Configuration
public class SentryConfig {

    @Bean
    @ConditionalOnExpression("!'${sentry.dsn:}'.isEmpty()")
    public OpenTelemetryOtlpEventProcessor openTelemetryOtlpEventProcessor() {
        return new OpenTelemetryOtlpEventProcessor();
    }
}
