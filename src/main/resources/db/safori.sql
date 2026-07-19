-- =============================================================================
-- SAFORI DDL
--
-- 이 프로젝트는 Flyway를 쓰지 않고 JPA `ddl-auto: update`로 스키마를 반영한다.
-- 그런데 hbm2ddl update는 "테이블/컬럼 추가"까지만 해주고, 이미 존재하는 테이블에
-- UNIQUE 제약이나 인덱스를 뒤늦게 붙여주지는 않는다. 즉 아래 DDL 중 UNIQUE 제약은
-- 애플리케이션 기동만으로는 생성되지 않으므로 각 DB에 한 번씩 직접 실행해야 한다.
--
-- 실행: mysql -h <host> -u <user> -p <database> < src/main/resources/db/safori.sql
-- 성격: 재실행 안전(idempotent). 이미 적용된 항목은 건너뛴다.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 2026-07 마음일기 세션 생성 정책 변경
--   기존: 마음일기 분석 완료 → 즉시 세션 1개 생성 (이벤트 트리거)
--   변경: 3일 연속 부정 감정(슬픔/분노/불안)일 때만 세션 생성 (10분 주기 스케줄러)
--
--   mind_diary_trigger 원장이 스케줄러의 멱등성 키다. 이게 없으면 스케줄러가 매 주기
--   같은 일기로 세션을 다시 만들어 무한 증식하고, 인스턴스를 늘렸을 때 동시 실행으로
--   중복 세션이 생긴다. 반드시 적용할 것.
-- -----------------------------------------------------------------------------

-- mind_diary_trigger: 일기 → 세션 트리거 원장
--   "일기 1건은 최대 한 번만 상담을 촉발한다"는 불변식을 데이터로 명시한다.
--   voice_id UNIQUE가 멱등성 키 — 스캔 쿼리는 이 테이블에 행이 있으면 후보에서 제외한다.
--
--   왜 세션이 아니라 원장으로 판정하나: 세션은 사용자가 지울 수 있는 가변 자원이다.
--   "세션이 있나"로 판정하면 사용자가 세션을 지운 순간 다음 스캔에서 같은 일기가 다시 후보가
--   되어 세션이 부활한다. 원장은 "트리거를 시도한 적 있다"는 불변 사실을 남긴다.
--
--   session_id ON DELETE SET NULL: 세션을 지워도 원장 행은 남아 재생성을 막는다.
--   voice_id   ON DELETE CASCADE : 일기를 지우면 원장도 사라진다(그 일기는 새로 쓰면
--                                   새 voice_id라 다시 트리거 대상이 된다).
--   status : OFFERED(제안, 모달 대기) → ACCEPTED(수락, 세션 생성) / DECLINED(거절).
--            어느 상태든 행이 있으면 스캔에서 제외된다(재제안 방지).
CREATE TABLE IF NOT EXISTS mind_diary_trigger (
    mind_diary_trigger_id BIGINT      NOT NULL AUTO_INCREMENT,
    voice_id              BIGINT      NOT NULL,
    status                VARCHAR(16) NOT NULL,
    session_id            CHAR(36)    NULL,
    reason                VARCHAR(64) NOT NULL,
    created_date          DATETIME(6) NULL,
    last_modified_date    DATETIME(6) NULL,
    PRIMARY KEY (mind_diary_trigger_id),
    CONSTRAINT uq_mdt_voice UNIQUE (voice_id),
    CONSTRAINT fk_mdt_voice FOREIGN KEY (voice_id)
        REFERENCES voice (voice_id) ON DELETE CASCADE,
    CONSTRAINT fk_mdt_session FOREIGN KEY (session_id)
        REFERENCES chat_session (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- chat_session_diary: 세션이 어떤 일기들을 근거로 만들어졌는지 (세션 N:M 일기)
--   연속 부정 감정 정책에서 세션 하나는 여러 날짜의 일기를 컨텍스트로 갖고,
--   일기 하나는 여러 세션에 걸쳐 쓰인다(수요일 일기가 수·목·금 세션의 컨텍스트).
--   seq: 컨텍스트 내 순서. 0부터 시간순(오래된 → 최신), 마지막이 트리거 일기.
CREATE TABLE IF NOT EXISTS chat_session_diary (
    chat_session_diary_id BIGINT      NOT NULL AUTO_INCREMENT,
    session_id            CHAR(36)    NOT NULL,
    voice_id              BIGINT      NOT NULL,
    seq                   INT         NOT NULL,
    created_date          DATETIME(6) NULL,
    last_modified_date    DATETIME(6) NULL,
    PRIMARY KEY (chat_session_diary_id),
    CONSTRAINT uq_csd_session_voice UNIQUE (session_id, voice_id),
    KEY idx_csd_voice (voice_id),
    CONSTRAINT fk_csd_session FOREIGN KEY (session_id)
        REFERENCES chat_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_csd_voice FOREIGN KEY (voice_id)
        REFERENCES voice (voice_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- -----------------------------------------------------------------------------
-- chat_message → chat_session FK에 ON DELETE CASCADE 보장.
--   메시지는 세션에 종속된 부품이다. 세션을 지우면 함께 삭제돼야 한다.
--   이게 없으면 메시지가 있는 세션은 DELETE 시 FK 위반으로 실패한다(삭제 API 500).
--   hbm2ddl update는 기존 FK의 ON DELETE 옵션을 바꿔주지 않으므로, 이미 존재하는(옵션 없는)
--   FK를 찾아 드롭하고 CASCADE 버전으로 다시 건다. (자동 생성된 FK 이름도 처리)
-- -----------------------------------------------------------------------------
SET @fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_message'
      AND COLUMN_NAME = 'session_id'
      AND REFERENCED_TABLE_NAME = 'chat_session'
    LIMIT 1
);
SET @sql := IF(@fk IS NOT NULL,
    CONCAT('ALTER TABLE chat_message DROP FOREIGN KEY ', @fk),
    'SELECT "no existing chat_message session FK" AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id)
        REFERENCES chat_session (id) ON DELETE CASCADE;


-- -----------------------------------------------------------------------------
-- voice 인덱스: 세션 트리거 스케줄러가 10분마다 voice를 스캔한다.
--   이 인덱스가 없으면 매 주기 full table scan이 발생한다(데이터가 쌓일수록 악화).
--   hbm2ddl update는 기존 테이블에 인덱스를 추가해주지 않으므로 직접 적용해야 한다.
--   (MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않아 존재 확인 후 실행한다)
--
--   idx_voice_status_completed : findUntriggeredVoiceIds
--       WHERE analysis_status=? AND analysis_completed_at>=? ORDER BY analysis_completed_at
--   idx_voice_user_created     : findEmotionRows(연속 감정 판정) + 주간/월간 리포트
--       WHERE user_id=? AND created_date BETWEEN ? AND ?
-- -----------------------------------------------------------------------------
SET @exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voice'
      AND INDEX_NAME = 'idx_voice_status_completed'
);
SET @sql := IF(@exists = 0,
    'ALTER TABLE voice ADD INDEX idx_voice_status_completed (analysis_status, analysis_completed_at)',
    'SELECT "idx_voice_status_completed already exists" AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voice'
      AND INDEX_NAME = 'idx_voice_user_created'
);
SET @sql := IF(@exists = 0,
    'ALTER TABLE voice ADD INDEX idx_voice_user_created (user_id, created_date)',
    'SELECT "idx_voice_user_created already exists" AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
