package com.safori.common.config;

import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화 + 스케줄러가 쓰는 설정 프로퍼티 바인딩.
 *
 * <p>인스턴스를 2대 이상으로 늘리면 ShedLock을 추가해 스케줄 중복 실행을 막는 것을 검토한다.
 * 데이터 정합성은 {@code chat_session.trigger_voice_id} UNIQUE 제약이 이미 보장하므로,
 * ShedLock이 막는 것은 LLM 중복 호출 비용이다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SessionTriggerProperties.class)
public class SchedulingConfig {
}
