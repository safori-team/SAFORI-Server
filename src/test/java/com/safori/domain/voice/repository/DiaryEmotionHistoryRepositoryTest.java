package com.safori.domain.voice.repository;

import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러가 쓰는 조회 쿼리 검증.
 *
 * <p>이 두 쿼리는 스케줄러에서만 호출되어 실서비스 트래픽으로 자연히 검증되지 않는다.
 * 잘못되면 세션이 아예 안 생기거나(후보 누락) 무한 생성된다(멱등성 실패).
 */
@DataJpaTest
class DiaryEmotionHistoryRepositoryTest {

    @Autowired DiaryEmotionHistoryRepository repository;
    @Autowired EntityManager em;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 12, 0);

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = persistUser("tester");
        otherUser = persistUser("other");
    }

    // -- findUntriggeredVoiceIds ---------------------------------------------

    @Test
    @DisplayName("분석 완료 + 세션 미생성 일기만 후보로 잡는다")
    void picksOnlyCompletedAndUntriggered() {
        Voice completed = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(completed, EmotionType.SAD);

        Voice pending = persistVoice(user, Voice.AnalysisStatus.PENDING, null);
        persistComposite(pending, EmotionType.SAD);

        Voice failed = persistVoice(user, Voice.AnalysisStatus.FAILED, NOW.minusHours(1));
        persistComposite(failed, EmotionType.SAD);

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).containsExactly(completed.getId());
    }

    @Test
    @DisplayName("이미 트리거된(원장에 행이 있는) 일기는 후보에서 빠진다 — 스케줄러 멱등성의 핵심")
    void excludesAlreadyTriggeredVoice() {
        Voice triggered = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(triggered, EmotionType.SAD);
        Voice untriggered = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(untriggered, EmotionType.SAD);

        persistTrigger(triggered, persistChatSession(user));

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).containsExactly(untriggered.getId());
    }

    @Test
    @DisplayName("세션이 삭제돼 원장.session_id가 NULL이어도 일기는 여전히 후보에서 빠진다 — 세션 부활 방지")
    void deletedSessionDoesNotRevive() {
        Voice voice = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(voice, EmotionType.SAD);
        persistTrigger(voice, null);  // 트리거는 됐으나 세션은 이미 삭제됨(SET NULL)

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("scan-lookback 밖의 오래된 일기는 후보에서 빠진다")
    void respectsScanLookback() {
        Voice old = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(72));
        persistComposite(old, EmotionType.SAD);
        Voice recent = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(recent, EmotionType.SAD);

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).containsExactly(recent.getId());
    }

    @Test
    @DisplayName("분석 결과(VoiceComposite)가 없는 일기는 후보가 아니다")
    void excludesVoiceWithoutComposite() {
        persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));  // composite 없음

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("오래된 것부터 처리하도록 분석 완료 시각 오름차순으로 반환한다")
    void ordersByAnalysisCompletedAtAsc() {
        Voice newer = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(1));
        persistComposite(newer, EmotionType.SAD);
        Voice older = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(5));
        persistComposite(older, EmotionType.SAD);

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 50));

        assertThat(candidates).containsExactly(older.getId(), newer.getId());
    }

    @Test
    @DisplayName("batch size만큼만 가져온다 — LLM 호출 폭주 방지")
    void respectsBatchSize() {
        for (int i = 0; i < 5; i++) {
            Voice v = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW.minusHours(i + 1));
            persistComposite(v, EmotionType.SAD);
        }

        List<Long> candidates = repository.findUntriggeredVoiceIds(
                NOW.minusHours(48), PageRequest.of(0, 2));

        assertThat(candidates).hasSize(2);
    }

    // -- findEmotionRows -----------------------------------------------------

    @Test
    @DisplayName("기간 내 일기의 AI 감정과 신고 감정을 함께 반환한다")
    void returnsAiAndReportedEmotion() {
        Voice voice = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(voice, EmotionType.NEUTRAL);
        persistReport(voice, user, EmotionType.SAD);

        List<Object[]> rows = repository.findEmotionRows(
                user.getId(), NOW.minusDays(3), NOW.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(voice.getId());
        assertThat(rows.get(0)[2]).isEqualTo(EmotionType.NEUTRAL);  // AI 분석
        assertThat(rows.get(0)[3]).isEqualTo(EmotionType.SAD);      // 사용자 신고
    }

    @Test
    @DisplayName("신고가 없으면 신고 감정 자리는 null이고 일기 자체는 누락되지 않는다 (LEFT JOIN)")
    void keepsRowWhenNoReport() {
        Voice voice = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(voice, EmotionType.SAD);

        List<Object[]> rows = repository.findEmotionRows(
                user.getId(), NOW.minusDays(3), NOW.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[2]).isEqualTo(EmotionType.SAD);
        assertThat(rows.get(0)[3]).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 신고는 딸려오지 않는다 — 연속 판정이 남의 신고로 오염되면 안 된다")
    void ignoresOtherUsersReport() {
        Voice voice = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(voice, EmotionType.SAD);
        persistReport(voice, otherUser, EmotionType.HAPPY);

        List<Object[]> rows = repository.findEmotionRows(
                user.getId(), NOW.minusDays(3), NOW.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[3]).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 일기는 조회되지 않는다")
    void filtersByUser() {
        Voice mine = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(mine, EmotionType.SAD);
        Voice theirs = persistVoice(otherUser, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(theirs, EmotionType.SAD);

        List<Object[]> rows = repository.findEmotionRows(
                user.getId(), NOW.minusDays(3), NOW.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(mine.getId());
    }

    @Test
    @DisplayName("조회 범위는 [start, end) — 경계 하루가 통째로 빠지거나 겹치지 않아야 한다")
    void rangeIsStartInclusiveEndExclusive() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 14, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 17, 0, 0);

        Voice before = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, start.minusSeconds(1));
        persistComposite(before, EmotionType.SAD);
        Voice atStart = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, start);
        persistComposite(atStart, EmotionType.SAD);
        Voice atEnd = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, end);
        persistComposite(atEnd, EmotionType.SAD);

        List<Object[]> rows = repository.findEmotionRows(user.getId(), start, end);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo(atStart.getId());
    }

    @Test
    @DisplayName("최신순으로 반환한다 — 스트릭을 오늘부터 거슬러 세기 때문")
    void ordersByCreatedDateDesc() {
        Voice older = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(2));
        persistComposite(older, EmotionType.SAD);
        Voice newer = persistVoice(user, Voice.AnalysisStatus.COMPLETED, NOW, NOW.minusDays(1));
        persistComposite(newer, EmotionType.SAD);

        List<Object[]> rows = repository.findEmotionRows(
                user.getId(), NOW.minusDays(3), NOW.plusDays(1));

        assertThat(rows).extracting(r -> r[0])
                .containsExactly(newer.getId(), older.getId());
    }

    // -- fixtures ------------------------------------------------------------

    private User persistUser(String username) {
        User u = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .username(username)
                .role(Role.USER)
                .password("pw")
                .name(username)
                .build();
        em.persist(u);
        return u;
    }

    private Voice persistVoice(User owner, Voice.AnalysisStatus status, LocalDateTime completedAt) {
        return persistVoice(owner, status, completedAt, NOW);
    }

    private Voice persistVoice(User owner, Voice.AnalysisStatus status,
                               LocalDateTime completedAt, LocalDateTime createdDate) {
        Voice v = Voice.builder()
                .voiceKey("key-" + UUID.randomUUID())
                .voiceTitle("t")
                .duration(10).sampleRate(16000).bitRate(128)
                .analysisStatus(status)
                .analysisCompletedAt(completedAt)
                .user(owner)
                .createdDate(createdDate)
                .build();
        em.persist(v);
        // @CreatedDate 감사(auditing)가 켜져 있으면 빌더 값이 덮이므로 강제로 되돌린다
        em.flush();
        em.createNativeQuery("UPDATE voice SET created_date = ?1 WHERE voice_id = ?2")
                .setParameter(1, createdDate)
                .setParameter(2, v.getId())
                .executeUpdate();
        em.clear();
        return v;
    }

    private void persistComposite(Voice voice, EmotionType topEmotion) {
        VoiceComposite c = VoiceComposite.builder()
                .voice(em.getReference(Voice.class, voice.getId()))
                .valenceX1000(0).arousalX1000(0).intensityX1000(0)
                .happyBps(0).sadBps(0).neutralBps(0)
                .angryBps(0).anxietyBps(0).surpriseBps(0)
                .topEmotion(topEmotion)
                .topEmotionConfidenceBps(5000)
                .build();
        em.persist(c);
        em.flush();
    }

    private void persistReport(Voice voice, User reporter, EmotionType reportedEmotion) {
        VoiceEmotionReport r = VoiceEmotionReport.builder()
                .voice(em.getReference(Voice.class, voice.getId()))
                .user(em.getReference(User.class, reporter.getId()))
                .reportedEmotion(reportedEmotion)
                .build();
        em.persist(r);
        em.flush();
    }

    private ChatSession persistChatSession(User owner) {
        ChatSession s = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .user(em.getReference(User.class, owner.getId()))
                .lastMessageAt(NOW)
                .build();
        em.persist(s);
        em.flush();
        return s;
    }

    /** 트리거 원장 행 삽입. session이 null이면 세션이 삭제된 상태(SET NULL)를 흉내낸다. */
    private void persistTrigger(Voice voice, ChatSession session) {
        MindDiaryTrigger t = MindDiaryTrigger.builder()
                .voice(em.getReference(Voice.class, voice.getId()))
                .session(session == null ? null
                        : em.getReference(ChatSession.class, session.getId()))
                .status(session == null
                        ? com.safori.domain.chatbot.entity.MindDiaryTriggerStatus.OFFERED
                        : com.safori.domain.chatbot.entity.MindDiaryTriggerStatus.ACCEPTED)
                .reason("TEST")
                .build();
        em.persist(t);
        em.flush();
    }
}
