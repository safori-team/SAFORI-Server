package com.safori.infra.ai.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.chatbot.model.CrisisAssessment;
import com.safori.domain.chatbot.model.CrisisLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiCrisisClassifierTest {

    @Test
    @DisplayName("Gemini 클라이언트가 없으면 상담을 막지 않는 UNKNOWN 판정을 반환한다")
    void noClientFailsOpen() {
        GeminiCrisisClassifier classifier =
                new GeminiCrisisClassifier(Optional.empty(), "gemini-2.5-flash", new ObjectMapper());

        CrisisAssessment result = classifier.classify("모호한 발화");

        assertThat(result.level()).isEqualTo(CrisisLevel.UNKNOWN);
        assertThat(result.requiresCrisisFlow()).isFalse();
    }

    @Test
    @DisplayName("모델 신뢰도는 서버가 허용하는 0~1 범위로 보정한다")
    void clampsConfidence() {
        GeminiCrisisClassifier classifier =
                new GeminiCrisisClassifier(Optional.empty(), "gemini-2.5-flash", new ObjectMapper());

        CrisisAssessment result = classifier.sanitize(new CrisisAssessment(
                CrisisLevel.IMMINENT, true, true, true, 1.4, null));

        assertThat(result.confidence()).isEqualTo(1.0);
        assertThat(result.reasonCode()).isEqualTo("UNSPECIFIED");
        assertThat(result.requiresCrisisFlow()).isTrue();
    }
}
