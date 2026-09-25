package com.safori.common.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 잠금: 같은 이름은 한 곳만 잡을 수 있고, 풀리면 다시 잡힌다. (ddl-auto가 shedlock 테이블을 만든다)
 */
@SpringBootTest
class SchedulerLockTest {

    @Autowired LockProvider lockProvider;

    @Test
    @DisplayName("다른 컨테이너가 잡은 잠금은 잡을 수 없고, 해제 후에는 잡을 수 있다")
    void onlyOneHolder() {
        LockConfiguration config = new LockConfiguration(Instant.now(), "scheduler-lock-test",
                Duration.ofMinutes(1), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(config);
        assertThat(first).isPresent();
        assertThat(lockProvider.lock(config)).isEmpty();

        first.get().unlock();
        Optional<SimpleLock> again = lockProvider.lock(config);
        assertThat(again).isPresent();
        again.get().unlock();
    }
}
