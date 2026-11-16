package com.safori.infra.ai.gemini;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.entity.Role;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.ai.gemini.dto.GeminiEmotionScore;
import com.safori.infra.ai.gemini.dto.GeminiSegment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분석 결과 저장이 한 트랜잭션 안에서 유니크 제약(uq_vcp_voice·uq_vc_voice·uq_vel_voice_label)을
 * 위반하지 않고 덮어써지는지 검증한다.
 */
@SpringBootTest
@Transactional
class VoiceAnalysisResultWriterTest {

    @Autowired VoiceAnalysisResultWriter writer;
    @Autowired EntityManager em;

    private Long voiceId;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .userUuid(UUID.randomUUID().toString())
                .username("writer-" + UUID.randomUUID())
                .role(Role.USER)
                .password("pw")
                .name("tester")
                .build();
        em.persist(user);
        Voice voice = Voice.builder()
                .voiceKey("key-" + UUID.randomUUID())
                .voiceTitle("t")
                .duration(10).sampleRate(16000).bitRate(128)
                .analysisStatus(Voice.AnalysisStatus.PROCESSING)
                .user(user)
                .build();
        em.persist(voice);
        em.flush();
        voiceId = voice.getId();
    }

    @Test
    @DisplayName("재분석 덮어쓰기 - 같은 트랜잭션에서 삭제 후 재저장해도 유니크 제약을 위반하지 않는다")
    void replace_overwritesWithinOneTransaction() {
        writer.save(voiceId, result("첫 분석", "sad", "sadness"));
        em.flush();
        em.clear();

        writer.replace(voiceId, result("다시 분석", "happy", "joy"));
        em.flush();
        em.clear();

        List<VoiceComposite> composites = em.createQuery(
                        "select c from VoiceComposite c where c.voice.id = :id", VoiceComposite.class)
                .setParameter("id", voiceId).getResultList();
        assertThat(composites).singleElement()
                .extracting(VoiceComposite::getTopEmotion).isEqualTo(EmotionType.HAPPY);

        List<VoiceContent> contents = em.createQuery(
                        "select c from VoiceContent c where c.voice.id = :id", VoiceContent.class)
                .setParameter("id", voiceId).getResultList();
        assertThat(contents).singleElement()
                .extracting(VoiceContent::getContent).isEqualTo("다시 분석");

        List<String> labels = em.createQuery(
                        "select l.label from VoiceEmotionLabel l where l.voice.id = :id", String.class)
                .setParameter("id", voiceId).getResultList();
        assertThat(labels).containsExactly("joy");

        assertThat(em.find(Voice.class, voiceId).getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("최초 저장 - 분석 상태는 바꾸지 않는다(마감은 호출측 몫)")
    void save_keepsAnalysisStatus() {
        writer.save(voiceId, result("첫 분석", "sad", "sadness"));
        em.flush();
        em.clear();

        assertThat(em.find(Voice.class, voiceId).getAnalysisStatus()).isEqualTo(Voice.AnalysisStatus.PROCESSING);
    }

    private static GeminiAnalysisResult result(String transcript, String category, String label) {
        GeminiSegment segment = new GeminiSegment("00:00", transcript, category,
                List.of(new GeminiEmotionScore(label, 0.9)), "차분함");
        return new GeminiAnalysisResult(transcript, "요약", "제목", List.of(segment), 7.0);
    }
}
