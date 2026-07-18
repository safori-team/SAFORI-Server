package com.safori.domain.chatbot.scheduler;

import com.safori.domain.chatbot.policy.DiaryEmotionHistoryPort;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.policy.SessionTriggerDecision;
import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MindDiarySessionSchedulerTest {

    @Mock DiaryEmotionHistoryPort historyPort;
    @Mock MindDiaryTriggerEvaluator evaluator;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock User user;

    SessionTriggerProperties props = new SessionTriggerProperties();
    MindDiarySessionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MindDiarySessionScheduler(props, historyPort, evaluator, chatbotDomainService);
        given(user.getUsername()).willReturn("tester");
    }

    private Voice voice(Long id) {
        return Voice.builder().id(id).voiceKey("key-" + id).build();
    }

    private void stubEval(Long voiceId, SessionTriggerDecision decision) {
        given(evaluator.evaluate(voiceId)).willReturn(
                new MindDiaryTriggerEvaluator.Evaluation(voice(voiceId), user, decision));
    }

    @Test
    @DisplayName("조건 충족이면 제안(OFFERED)을 기록한다 — 세션·Gemini는 만들지 않는다")
    void recordsOfferWhenEligible() {
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of(10L));
        stubEval(10L, SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D", List.of(10L)));

        scheduler.run();

        verify(chatbotDomainService).recordOffer(any(Voice.class), eq("CONSECUTIVE_NEGATIVE_3D"));
    }

    @Test
    @DisplayName("조건 미충족이면 제안을 기록하지 않는다")
    void skipDoesNotRecordOffer() {
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of(10L));
        stubEval(10L, SessionTriggerDecision.skip("NOT_NEGATIVE"));

        scheduler.run();

        verify(chatbotDomainService, never()).recordOffer(any(), anyString());
    }

    @Test
    @DisplayName("후보 하나가 실패해도 나머지는 계속 처리한다")
    void oneFailureDoesNotStopBatch() {
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of(10L, 11L));
        given(evaluator.evaluate(10L)).willThrow(new IllegalStateException("boom"));
        stubEval(11L, SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D", List.of(11L)));

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();

        verify(chatbotDomainService, times(1)).recordOffer(any(Voice.class), anyString());
    }

    @Test
    @DisplayName("다른 인스턴스가 먼저 제안해 원장 UNIQUE 충돌이면 예외를 삼키고 넘어간다")
    void duplicateOfferSkippedQuietly() {
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of(10L));
        stubEval(10L, SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D", List.of(10L)));
        org.mockito.BDDMockito.willThrow(
                        new org.springframework.dao.DataIntegrityViolationException("uq_mdt_voice"))
                .given(chatbotDomainService).recordOffer(any(Voice.class), anyString());

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("후보가 없으면 아무 일도 하지 않는다")
    void noCandidatesIsNoop() {
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of());

        scheduler.run();

        verify(evaluator, never()).evaluate(any());
        verify(chatbotDomainService, never()).recordOffer(any(), anyString());
    }

    @Test
    @DisplayName("설정한 배치 크기만큼만 후보를 가져온다")
    void usesConfiguredBatchSize() {
        props.setScanBatchSize(7);
        given(historyPort.findUntriggeredVoiceIds(any(), anyInt())).willReturn(List.of());

        scheduler.run();

        verify(historyPort).findUntriggeredVoiceIds(any(java.time.LocalDateTime.class), eq(7));
    }
}
