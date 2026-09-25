package com.safori.common.config;

import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * 스케줄러 활성화 + 스케줄러가 쓰는 설정 프로퍼티 바인딩.
 *
 * <p>인스턴스를 2대 이상으로 늘리면 ShedLock을 추가해 스케줄 중복 실행을 막는 것을 검토한다.
 * 데이터 정합성은 {@code chat_session.trigger_voice_id} UNIQUE 제약이 이미 보장하므로,
 * ShedLock이 막는 것은 LLM 중복 호출 비용이다.
 */
/**
 * 스케줄러. 컨테이너가 여러 개여도(서버 증설, blue/green 배포 중 겹침) 각 작업은 ShedLock으로 한 곳에서만 실행된다.
 * 잠금은 DB {@code shedlock} 테이블에 잡고 시각은 DB 시계를 쓴다(서버 간 시계 차이 무관).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableConfigurationProperties(SessionTriggerProperties.class)
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
