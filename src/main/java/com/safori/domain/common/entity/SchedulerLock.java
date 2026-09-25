package com.safori.domain.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ShedLock 잠금 테이블. 애플리케이션 코드는 이 엔티티를 쓰지 않고 ShedLock이 JDBC로 직접 읽고 쓴다.
 * 엔티티로 둔 이유는 {@code ddl-auto}가 모든 환경(alpha·prod·테스트 H2)에 테이블을 만들게 하기 위해서다.
 */
@Entity
@Table(name = "shedlock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerLock {

    @Id
    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "lock_until", nullable = false)
    private LocalDateTime lockUntil;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;
}
