package com.safori.common.config;

import com.safori.domain.chatbot.policy.ConversationLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 챗봇 대화 정책 설정 바인딩.
 * (마음일기 세션 생성 정책 설정은 {@link SchedulingConfig}에 있다 — 스케줄러가 소유자라서)
 */
@Configuration
@EnableConfigurationProperties(ConversationLimitProperties.class)
public class ChatbotConfig {
}
