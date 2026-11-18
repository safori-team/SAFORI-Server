package com.safori.api.operator.service;

import com.safori.api.operator.dto.OperatorMindDiaryStatusResponse;
import com.safori.api.operator.dto.TriggerMindDiaryOfferResponse;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.policy.DailyDiaryEmotion;
import com.safori.domain.chatbot.policy.DiaryEmotionHistoryPort;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.policy.SessionTriggerDecision;
import com.safori.domain.chatbot.policy.SessionTriggerProperties;
import com.safori.domain.chatbot.repository.MindDiaryTriggerRepository;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.user.adaptor.UserAdaptor;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperatorMindDiaryUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock DiaryEmotionHistoryPort historyPort;
    @Mock MindDiaryTriggerEvaluator evaluator;
    @Mock MindDiaryTriggerRepository triggerRepository;
    @Mock ChatbotDomainService chatbotDomainService;

    SessionTriggerProperties props = new SessionTriggerProperties();
    User user = User.builder().id(7L).username("tester").build();
    OperatorMindDiaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorMindDiaryUseCase(
                userAdaptor, props, historyPort, evaluator, triggerRepository, chatbotDomainService);
        given(userAdaptor.queryUserByUsername("tester")).willReturn(user);
        given(triggerRepository.findByVoice_User_IdAndStatusOrderByCreatedDateDesc(any(), any()))
                .willReturn(List.of());
    }

    private void stubEval(Long voiceId, SessionTriggerDecision decision) {
        given(evaluator.evaluate(voiceId)).willReturn(new MindDiaryTriggerEvaluator.Evaluation(
                Voice.builder().id(voiceId).user(user).build(), user, decision));
    }

    @Test
    @DisplayName("최신 일기부터 평가해 처음 조건을 채우는 일기 하나로만 제안한다")
    void offersOnFirstEligibleCandidate() {
        given(historyPort.findUntriggeredVoiceIdsOfUser(eq(7L), anyInt())).willReturn(List.of(16L, 15L, 14L));
        stubEval(16L, SessionTriggerDecision.skip("STREAK_TOO_SHORT_2"));
        stubEval(15L, SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D_STREAK_3", List.of(15L)));

        TriggerMindDiaryOfferResponse response = useCase.triggerOffer("tester");

        assertThat(response.offered()).isTrue();
        assertThat(response.voiceId()).isEqualTo(15L);
        assertThat(response.reason()).isEqualTo("CONSECUTIVE_NEGATIVE_3D_STREAK_3");
        verify(chatbotDomainService).recordOffer(any(Voice.class), eq("CONSECUTIVE_NEGATIVE_3D_STREAK_3"));
        verify(evaluator, never()).evaluate(14L);
    }

    @Test
    @DisplayName("조건을 채우는 일기가 없으면 최신 일기의 보류 사유를 준다")
    void returnsLatestSkipReason() {
        given(historyPort.findUntriggeredVoiceIdsOfUser(eq(7L), anyInt())).willReturn(List.of(16L, 15L));
        stubEval(16L, SessionTriggerDecision.skip("STREAK_TOO_SHORT_2"));
        stubEval(15L, SessionTriggerDecision.skip("NOT_NEGATIVE"));

        TriggerMindDiaryOfferResponse response = useCase.triggerOffer("tester");

        assertThat(response.offered()).isFalse();
        assertThat(response.voiceId()).isEqualTo(16L);
        assertThat(response.reason()).isEqualTo("STREAK_TOO_SHORT_2");
        verify(chatbotDomainService, never()).recordOffer(any(), anyString());
    }

    @Test
    @DisplayName("평가할 일기가 없으면 NO_CANDIDATE")
    void noCandidate() {
        given(historyPort.findUntriggeredVoiceIdsOfUser(eq(7L), anyInt())).willReturn(List.of());

        TriggerMindDiaryOfferResponse response = useCase.triggerOffer("tester");

        assertThat(response.offered()).isFalse();
        assertThat(response.voiceId()).isNull();
        assertThat(response.reason()).isEqualTo(OperatorMindDiaryUseCase.NO_CANDIDATE);
    }

    @Test
    @DisplayName("스케줄러가 같은 일기로 먼저 제안했으면(원장 UNIQUE 위반) 예외 없이 ALREADY_TRIGGERED")
    void duplicateOfferIsAlreadyTriggered() {
        given(historyPort.findUntriggeredVoiceIdsOfUser(eq(7L), anyInt())).willReturn(List.of(16L));
        stubEval(16L, SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D_STREAK_3", List.of(16L)));
        willThrow(new DataIntegrityViolationException("uq_mdt_voice"))
                .given(chatbotDomainService).recordOffer(any(), anyString());

        TriggerMindDiaryOfferResponse response = useCase.triggerOffer("tester");

        assertThat(response.offered()).isFalse();
        assertThat(response.reason()).isEqualTo(OperatorMindDiaryUseCase.ALREADY_TRIGGERED);
    }

    @Test
    @DisplayName("연속 부정 일수는 오늘부터 거꾸로 세고, 같은 날 일기가 여럿이면 최신 것만 본다")
    void streakEndingTodayUsesLatestDiaryPerDay() {
        LocalDate today = LocalDate.now();
        given(historyPort.findDailyEmotions(eq(7L), any(), eq(today))).willReturn(List.of(
                new DailyDiaryEmotion(today, 18L, EmotionType.SAD),
                new DailyDiaryEmotion(today.minusDays(1), 17L, EmotionType.ANXIETY),
                new DailyDiaryEmotion(today.minusDays(1), 16L, EmotionType.HAPPY),
                new DailyDiaryEmotion(today.minusDays(2), 15L, EmotionType.HAPPY),
                new DailyDiaryEmotion(today.minusDays(3), 14L, EmotionType.SAD)));
        given(triggerRepository.findByVoice_IdIn(any())).willReturn(List.of(
                MindDiaryTrigger.offer(Voice.builder().id(18L).build(), "CONSECUTIVE_NEGATIVE_3D_STREAK_3")));

        OperatorMindDiaryStatusResponse response = useCase.status("tester", 7);

        assertThat(response.negativeStreakDays()).isEqualTo(2);
        assertThat(response.requiredConsecutiveDays()).isEqualTo(3);
        assertThat(response.diaries()).hasSize(5);
        assertThat(response.diaries().get(0).offerStatus()).isNotNull();
        assertThat(response.diaries().get(1).offerStatus()).isNull();
        assertThat(response.offer().hasOffer()).isFalse();
    }

    @Test
    @DisplayName("오늘 일기가 없으면 연속 부정 일수는 0")
    void streakIsZeroWithoutTodayDiary() {
        LocalDate today = LocalDate.now();
        given(historyPort.findDailyEmotions(eq(7L), any(), eq(today))).willReturn(List.of(
                new DailyDiaryEmotion(today.minusDays(1), 17L, EmotionType.SAD)));
        given(triggerRepository.findByVoice_IdIn(any())).willReturn(List.of());

        assertThat(useCase.status("tester", 7).negativeStreakDays()).isZero();
    }
}
