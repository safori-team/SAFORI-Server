package com.safori.domain.voice.repository;

import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 벌크 라벨 교체가 <b>호출측</b> 영속성 컨텍스트를 망가뜨리지 않는지 검증한다.
 *
 * <p>소분류 응답 반영은 같은 트랜잭션에서 라벨을 교체한 뒤 요청 원장과 일기를 마감한다.
 * 벌크 삭제에 {@code clearAutomatically = true}가 붙어 있으면 그 시점에 컨텍스트가 비워져
 * 호출측이 들고 있던 LAZY 프록시가 detach되고, 이후 프록시를 만지는 순간
 * {@code LazyInitializationException}으로 응답이 영원히 반영되지 않는다(ACK도 못 해 재수신
 * 루프가 된다). 어댑터를 mock으로 세운 단위 테스트로는 잡히지 않아 여기서 고정한다.
 */
@DataJpaTest
class VoiceEmotionLabelRepositoryTest {

    @Autowired VoiceEmotionLabelRepository repository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("벌크 삭제 후에도 호출측 LAZY 프록시가 살아 있다")
    void bulkDeleteKeepsCallerPersistenceContext() {
        Long voiceId = persistVoiceWithLabel();
        em.flush();
        em.clear();

        // 응답 반영 경로가 request.getVoice()로 받는 것과 같은 미초기화 프록시.
        Voice proxy = em.getReference(Voice.class, voiceId);

        repository.deleteAllByVoiceIdInBulk(voiceId);

        assertThatCode(proxy::markAnalysisCompleted).doesNotThrowAnyException();
        assertThat(em.contains(proxy)).isTrue();
    }

    @Test
    @DisplayName("같은 트랜잭션에서 삭제 → 재삽입해도 uq_vel_voice_label을 위반하지 않는다")
    void deleteThenReinsertSameLabel() {
        Long voiceId = persistVoiceWithLabel();
        em.flush();
        em.clear();

        Voice voice = em.getReference(Voice.class, voiceId);

        repository.deleteAllByVoiceIdInBulk(voiceId);
        repository.saveAll(List.of(label(voice, "joy", 900)));

        assertThatCode(em::flush).doesNotThrowAnyException();
        assertThat(repository.findByVoice_Id(voiceId))
                .singleElement()
                .satisfies(saved -> assertThat(saved.getIntensityX1000()).isEqualTo(900));
    }

    private Long persistVoiceWithLabel() {
        Voice voice = Voice.builder()
                .voiceKey("voices/test.m4a")
                .voiceTitle("테스트 일기")
                .duration(10)
                .sampleRate(44100)
                .bitRate(128000)
                .analysisStatus(Voice.AnalysisStatus.PROCESSING)
                .build();
        em.persist(voice);
        em.persist(label(voice, "joy", 500));
        return voice.getId();
    }

    private VoiceEmotionLabel label(Voice voice, String label, int intensity) {
        return VoiceEmotionLabel.builder()
                .voice(voice)
                .category("happy")
                .label(label)
                .intensityX1000(intensity)
                .build();
    }
}
