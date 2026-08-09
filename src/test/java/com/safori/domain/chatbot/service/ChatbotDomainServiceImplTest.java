package com.safori.domain.chatbot.service;

import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.ChatSessionDiary;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.common.event.MindDiaryOfferedEvent;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.policy.SessionTriggerDecision;
import com.safori.domain.chatbot.repository.ChatSessionDiaryRepository;
import com.safori.domain.chatbot.repository.MindDiaryTriggerRepository;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotDomainServiceImplTest {

    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock ChatSessionDiaryRepository chatSessionDiaryRepository;
    @Mock MindDiaryTriggerRepository mindDiaryTriggerRepository;
    @Mock MindDiaryTriggerEvaluator evaluator;
    @Mock ChatbotMessageMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks ChatbotDomainServiceImpl service;

    private final ChatbotReply reply = ChatbotReply.fallback();

    private User user(Long id) {
        return User.builder().id(id).username("u" + id).build();
    }

    private Voice voice(Long id, User owner) {
        return Voice.builder().id(id).voiceKey("key-" + id).user(owner).build();
    }

    // -- recordOffer ---------------------------------------------------------

    @Test
    @DisplayName("조건 충족 시 OFFERED 원장을 기록한다 (세션 없음)")
    void recordOfferSavesOfferedLedger() {
        Voice trigger = voice(10L, user(1L));
        given(mindDiaryTriggerRepository.save(any(MindDiaryTrigger.class)))
                .willAnswer(inv -> inv.getArgument(0));

        service.recordOffer(trigger, "CONSECUTIVE_NEGATIVE_3D");

        ArgumentCaptor<MindDiaryTrigger> cap = ArgumentCaptor.forClass(MindDiaryTrigger.class);
        verify(mindDiaryTriggerRepository).save(cap.capture());
        assertThat(cap.getValue().isOffered()).isTrue();
        assertThat(cap.getValue().getSession()).isNull();
        assertThat(cap.getValue().getReason()).isEqualTo("CONSECUTIVE_NEGATIVE_3D");

        // 제안 알림 이벤트가 대상 사용자로 발행된다.
        ArgumentCaptor<MindDiaryOfferedEvent> evt = ArgumentCaptor.forClass(MindDiaryOfferedEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertThat(evt.getValue().userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("원장 voice_id UNIQUE 충돌은 잡지 않고 전파한다 (트랜잭션 밖에서 처리)")
    void recordOfferPropagatesDuplicate() {
        willThrow(new DataIntegrityViolationException("uq_mdt_voice"))
                .given(mindDiaryTriggerRepository).save(any(MindDiaryTrigger.class));

        assertThatThrownBy(() -> service.recordOffer(voice(10L, user(1L)), "R"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -- getOfferableOffer ---------------------------------------------------

    @Test
    @DisplayName("제안 검증: 없으면 OFFER_NOT_FOUND")
    void getOfferable_notFound() {
        given(mindDiaryTriggerRepository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOfferableOffer(99L, user(1L)))
                .isSameAs(ChatbotHandler.OFFER_NOT_FOUND);
    }

    @Test
    @DisplayName("제안 검증: 다른 사용자면 OFFER_NO_PERMISSION")
    void getOfferable_noPermission() {
        MindDiaryTrigger offer = MindDiaryTrigger.offer(voice(10L, user(1L)), "R");
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));
        assertThatThrownBy(() -> service.getOfferableOffer(5L, user(2L)))
                .isSameAs(ChatbotHandler.OFFER_NO_PERMISSION);
    }

    @Test
    @DisplayName("제안 검증: 이미 응답한 제안이면 OFFER_NOT_OFFERABLE")
    void getOfferable_notOfferable() {
        MindDiaryTrigger offer = MindDiaryTrigger.offer(voice(10L, user(1L)), "R");
        offer.decline();  // 더 이상 OFFERED 아님
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));
        assertThatThrownBy(() -> service.getOfferableOffer(5L, user(1L)))
                .isSameAs(ChatbotHandler.OFFER_NOT_OFFERABLE);
    }

    @Test
    @DisplayName("제안 검증: 소유자 + OFFERED면 통과")
    void getOfferable_ok() {
        MindDiaryTrigger offer = MindDiaryTrigger.offer(voice(10L, user(1L)), "R");
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));
        assertThat(service.getOfferableOffer(5L, user(1L))).isSameAs(offer);
    }

    // -- createSessionForAcceptedOffer --------------------------------------

    @Test
    @DisplayName("수락 시 세션·첫 메시지·컨텍스트 링크를 저장하고 원장을 ACCEPTED로 전환한다")
    void acceptCreatesSessionAndFlipsLedger() {
        User u = user(1L);
        Voice d1 = voice(10L, u), trigger = voice(12L, u);
        MindDiaryTrigger offer = MindDiaryTrigger.offer(trigger, "CONSECUTIVE_NEGATIVE_3D");
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));
        given(chatSessionAdaptor.save(any(ChatSession.class))).willAnswer(i -> i.getArgument(0));

        String sessionId = service.createSessionForAcceptedOffer(
                u, 5L, trigger, List.of(d1, trigger), reply);

        assertThat(sessionId).isNotBlank();
        // 첫 메시지: 사용자 발화 없이 MIND_DIARY
        ArgumentCaptor<ChatMessage> msg = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageAdaptor).save(msg.capture());
        assertThat(msg.getValue().getUserInput()).isNull();
        assertThat(msg.getValue().getOrigin()).isEqualTo(MessageOrigin.MIND_DIARY);
        // 컨텍스트 seq 순서
        ArgumentCaptor<ChatSessionDiary> diary = ArgumentCaptor.forClass(ChatSessionDiary.class);
        verify(chatSessionDiaryRepository, org.mockito.Mockito.times(2)).save(diary.capture());
        assertThat(diary.getAllValues()).extracting(ChatSessionDiary::getSeq).containsExactly(0, 1);
        // 원장 상태 전환
        assertThat(offer.isOffered()).isFalse();
        assertThat(offer.getSession().getId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("수락 재확인 사이 다른 요청이 먼저 처리했으면 OFFER_NOT_OFFERABLE")
    void acceptRejectsIfNoLongerOffered() {
        MindDiaryTrigger offer = MindDiaryTrigger.offer(voice(12L, user(1L)), "R");
        offer.decline();
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.createSessionForAcceptedOffer(
                user(1L), 5L, voice(12L, user(1L)), List.of(voice(12L, user(1L))), reply))
                .isSameAs(ChatbotHandler.OFFER_NOT_OFFERABLE);
    }

    // -- declineOffer --------------------------------------------------------

    @Test
    @DisplayName("거절 시 원장을 DECLINED로 전환한다")
    void declineFlipsLedger() {
        MindDiaryTrigger offer = MindDiaryTrigger.offer(voice(12L, user(1L)), "R");
        given(mindDiaryTriggerRepository.findById(5L)).willReturn(Optional.of(offer));

        service.declineOffer(5L);

        assertThat(offer.isOffered()).isFalse();
    }

    // -- withdrawStaleTriggers (#6) -----------------------------------------

    private MindDiaryTrigger offeredTrigger(Long voiceId, User u) {
        return MindDiaryTrigger.offer(voice(voiceId, u), "R");
    }

    private MindDiaryTrigger acceptedTrigger(Long voiceId, User u, ChatSession session) {
        MindDiaryTrigger t = MindDiaryTrigger.offer(voice(voiceId, u), "R");
        t.accept(session);
        return t;
    }

    private void stubStale(Long voiceId) {  // 더 이상 조건 불충족
        given(evaluator.evaluate(voiceId)).willReturn(new MindDiaryTriggerEvaluator.Evaluation(
                voice(voiceId, user(1L)), user(1L), SessionTriggerDecision.skip("NOT_NEGATIVE")));
    }

    private void stubStillValid(Long voiceId) {
        given(evaluator.evaluate(voiceId)).willReturn(new MindDiaryTriggerEvaluator.Evaluation(
                voice(voiceId, user(1L)), user(1L),
                SessionTriggerDecision.create("CONSECUTIVE_NEGATIVE_3D", List.of(voiceId))));
    }

    @Test
    @DisplayName("재분석으로 조건이 깨진 OFFERED 제안은 철회(삭제)된다")
    void withdrawsStaleOffer() {
        MindDiaryTrigger offer = offeredTrigger(10L, user(1L));
        given(mindDiaryTriggerRepository.findByVoice_User_IdAndStatusIn(any(), any()))
                .willReturn(List.of(offer));
        stubStale(10L);

        service.withdrawStaleTriggers(1L);

        verify(mindDiaryTriggerRepository).delete(offer);
        verify(chatSessionAdaptor, never()).delete(any());
    }

    @Test
    @DisplayName("조건이 깨진 ACCEPTED이고 발화 0회면 세션과 원장을 함께 삭제한다")
    void withdrawsZeroTurnAcceptedSession() {
        ChatSession session = ChatSession.create(user(1L));
        MindDiaryTrigger accepted = acceptedTrigger(11L, user(1L), session);
        given(mindDiaryTriggerRepository.findByVoice_User_IdAndStatusIn(any(), any()))
                .willReturn(List.of(accepted));
        stubStale(11L);
        given(chatMessageAdaptor.countUserTurnsBySessionId(session.getId())).willReturn(0L);

        service.withdrawStaleTriggers(1L);

        verify(mindDiaryTriggerRepository).delete(accepted);
        verify(chatSessionAdaptor).delete(session);
    }

    @Test
    @DisplayName("조건이 깨져도 발화가 1회 이상인 세션은 보존한다 — 진행된 상담은 기록으로 남긴다")
    void keepsAcceptedSessionWithConversation() {
        ChatSession session = ChatSession.create(user(1L));
        MindDiaryTrigger accepted = acceptedTrigger(11L, user(1L), session);
        given(mindDiaryTriggerRepository.findByVoice_User_IdAndStatusIn(any(), any()))
                .willReturn(List.of(accepted));
        stubStale(11L);
        given(chatMessageAdaptor.countUserTurnsBySessionId(session.getId())).willReturn(3L);

        service.withdrawStaleTriggers(1L);

        verify(mindDiaryTriggerRepository, never()).delete(any());
        verify(chatSessionAdaptor, never()).delete(any());
    }

    @Test
    @DisplayName("여전히 조건을 만족하는 트리거는 건드리지 않는다")
    void keepsStillValidTrigger() {
        MindDiaryTrigger offer = offeredTrigger(10L, user(1L));
        given(mindDiaryTriggerRepository.findByVoice_User_IdAndStatusIn(any(), any()))
                .willReturn(List.of(offer));
        stubStillValid(10L);

        service.withdrawStaleTriggers(1L);

        verify(mindDiaryTriggerRepository, never()).delete(any());
    }

    // -- deleteTriggerAndSessionByVoiceId (일기 삭제) -------------------------

    @Test
    @DisplayName("일기가 트리거한 세션이 있으면 세션과 원장 행을 모두 삭제한다")
    void deletesTriggeredSession() {
        ChatSession session = ChatSession.create(user(1L));
        MindDiaryTrigger t = acceptedTrigger(12L, user(1L), session);
        given(mindDiaryTriggerRepository.findByVoice_Id(12L)).willReturn(Optional.of(t));

        service.deleteTriggerAndSessionByVoiceId(12L);

        verify(chatSessionAdaptor).delete(session);
        verify(mindDiaryTriggerRepository).delete(t);
    }

    @Test
    @DisplayName("트리거 이력이 없으면 세션도 원장도 삭제하지 않는다")
    void noLedgerNoDelete() {
        given(mindDiaryTriggerRepository.findByVoice_Id(99L)).willReturn(Optional.empty());
        service.deleteTriggerAndSessionByVoiceId(99L);
        verify(chatSessionAdaptor, never()).delete(any());
        verify(mindDiaryTriggerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("OFFERED 원장(세션 없음)이면 세션은 건너뛰고 원장 행만 삭제한다")
    void offeredLedgerNoSessionToDelete() {
        MindDiaryTrigger offered = offeredTrigger(12L, user(1L));
        given(mindDiaryTriggerRepository.findByVoice_Id(12L)).willReturn(Optional.of(offered));
        service.deleteTriggerAndSessionByVoiceId(12L);
        verify(chatSessionAdaptor, never()).delete(any());
        verify(mindDiaryTriggerRepository).delete(offered);
    }
}
