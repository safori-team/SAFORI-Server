package com.safori.domain.chatbot.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.safori.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.ChatSessionDiary;
import com.safori.domain.chatbot.entity.DoranEmotion;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.entity.MindDiaryTriggerStatus;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.repository.ChatSessionDiaryRepository;
import com.safori.domain.chatbot.repository.MindDiaryTriggerRepository;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DomainService
@RequiredArgsConstructor
public class ChatbotDomainServiceImpl implements ChatbotDomainService {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatSessionDiaryRepository chatSessionDiaryRepository;
    private final MindDiaryTriggerRepository mindDiaryTriggerRepository;
    private final MindDiaryTriggerEvaluator evaluator;
    private final ChatbotMessageMapper mapper;

    @Override
    public void verifyOwnership(ChatSession session, User user) {
        if (!session.getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.SESSION_NO_PERMISSION;
        }
    }

    @Override
    public void verifyOwnership(ChatMessage message, User user) {
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.MESSAGE_NO_PERMISSION;
        }
    }

    @Override
    public DoranEmotion parseFeedbackEmotion(String emotionCode) {
        try {
            return DoranEmotion.fromCode(emotionCode);
        } catch (IllegalArgumentException e) {
            throw ChatbotHandler.FEEDBACK_INVALID_EMOTION;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryTurn> loadRecentHistory(String sessionId, int limit) {
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(sessionId, limit);
        Collections.reverse(recent);  // 시간순으로
        List<HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            history.add(new HistoryTurn(
                    m.getUserInput(), mapper.botMessagePreview(m.getBotResponse())));
        }
        return history;
    }

    @Override
    @Transactional(readOnly = true)
    public long countTurns(String sessionId) {
        return chatMessageAdaptor.countBySessionId(sessionId);
    }

    @Override
    @Transactional
    public Long appendMessage(String sessionId, String userInput, ChatbotReply botReply,
                              MessageOrigin origin, String voiceKey) {
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(userInput)
                .botResponse(mapper.toBotResponseJson(botReply))
                .voiceKey(voiceKey)
                .origin(origin)
                .build());
        session.touch();
        chatSessionAdaptor.save(session);
        return saved.getId();
    }

    @Override
    @Transactional
    public void recordOffer(Voice triggerVoice, String reason) {
        // 멱등성 키. voice_id UNIQUE 위반 시 예외를 잡지 않고 전파한다 — 트랜잭션 안에서
        // 잡으면 rollback-only로 마킹돼 커밋 시 UnexpectedRollbackException이 터진다.
        // 대신 트랜잭션이 깨끗이 롤백되고, 호출자(스케줄러)가 중복으로 판단해 건너뛴다.
        mindDiaryTriggerRepository.save(MindDiaryTrigger.offer(triggerVoice, reason));
    }

    @Override
    @Transactional(readOnly = true)
    public MindDiaryTrigger getOfferableOffer(Long offerId, User user) {
        MindDiaryTrigger offer = mindDiaryTriggerRepository.findById(offerId)
                .orElseThrow(() -> ChatbotHandler.OFFER_NOT_FOUND);
        if (!offer.getVoice().getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.OFFER_NO_PERMISSION;
        }
        if (!offer.isOffered()) {
            throw ChatbotHandler.OFFER_NOT_OFFERABLE;
        }
        return offer;
    }

    @Override
    @Transactional
    public String createSessionForAcceptedOffer(User user, Long offerId, Voice triggerVoice,
                                                List<Voice> contextVoices, ChatbotReply botReply) {
        MindDiaryTrigger offer = mindDiaryTriggerRepository.findById(offerId)
                .orElseThrow(() -> ChatbotHandler.OFFER_NOT_FOUND);
        if (!offer.isOffered()) {
            // 재확인 사이에 다른 요청이 먼저 수락/거절했을 수 있다 (더블탭 등).
            throw ChatbotHandler.OFFER_NOT_OFFERABLE;
        }
        ChatSession session = ChatSession.create(user);
        chatSessionAdaptor.save(session);
        chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(null)
                .botResponse(mapper.toBotResponseJson(botReply))
                .voice(triggerVoice)
                .origin(MessageOrigin.MIND_DIARY)
                .build());
        for (int seq = 0; seq < contextVoices.size(); seq++) {
            chatSessionDiaryRepository.save(
                    ChatSessionDiary.of(session, contextVoices.get(seq), seq));
        }
        offer.accept(session);          // OFFERED → ACCEPTED, session_id 연결 (managed → dirty checking)
        return session.getId();
    }

    @Override
    @Transactional
    public void declineOffer(Long offerId) {
        MindDiaryTrigger offer = mindDiaryTriggerRepository.findById(offerId)
                .orElseThrow(() -> ChatbotHandler.OFFER_NOT_FOUND);
        if (!offer.isOffered()) {
            throw ChatbotHandler.OFFER_NOT_OFFERABLE;
        }
        offer.decline();
    }

    @Override
    @Transactional
    public void withdrawStaleTriggers(Long userId) {
        List<MindDiaryTrigger> active = mindDiaryTriggerRepository
                .findByVoice_User_IdAndStatusIn(userId,
                        List.of(MindDiaryTriggerStatus.OFFERED, MindDiaryTriggerStatus.ACCEPTED));
        for (MindDiaryTrigger t : active) {
            if (evaluator.evaluate(t.getVoice().getId()).shouldCreate()) {
                continue;  // 여전히 조건 충족 — 유지
            }
            if (t.isOffered()) {
                mindDiaryTriggerRepository.delete(t);  // 아직 세션 없음 → 제안만 철회
                continue;
            }
            // ACCEPTED: 대화가 없었으면(0턴) 폐기, 있었으면 기록 보존
            ChatSession session = t.getSession();
            if (session != null
                    && chatMessageAdaptor.countUserTurnsBySessionId(session.getId()) == 0) {
                mindDiaryTriggerRepository.delete(t);  // 세션 FK 참조 먼저 제거
                chatSessionAdaptor.delete(session);    // 메시지/컨텍스트 링크 cascade
            }
        }
    }

    @Override
    @Transactional
    public void deleteTriggeredSessionByVoiceId(Long voiceId) {
        mindDiaryTriggerRepository.findByVoice_Id(voiceId).ifPresent(trigger -> {
            ChatSession session = trigger.getSession();
            if (session != null) {
                // 세션 삭제 → chat_message·chat_session_diary는 FK CASCADE로,
                //           원장.session_id는 FK SET NULL로 정리된다.
                chatSessionAdaptor.delete(session);
            }
        });
    }
}
