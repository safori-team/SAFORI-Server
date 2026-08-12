package com.safori.domain.chatbot.service;

import com.safori.domain.chatbot.entity.ChatMessage;
import com.safori.domain.chatbot.entity.ChatSession;
import com.safori.domain.chatbot.entity.DoranEmotion;
import com.safori.domain.chatbot.entity.MessageOrigin;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;

import java.util.List;

public interface ChatbotDomainService {

    void verifyOwnership(ChatSession session, User user);

    void verifyOwnership(ChatMessage message, User user);

    DoranEmotion parseFeedbackEmotion(String emotionCode);

    /**
     * 채팅 세션의 최근 N개 메시지를 가져와 시간순(오래된 → 최근) HistoryTurn으로 변환한다.
     * (readOnly 트랜잭션 경계)
     */
    List<HistoryTurn> loadRecentHistory(String sessionId, int limit);


    /**
     * 메시지 INSERT + 세션 lastMessageAt 갱신을 한 트랜잭션으로 처리한다.
     * 응답이 이미 확정된 경우(LLM을 거치지 않는 안전 응답 등)에만 쓴다.
     * @return 생성된 메시지 ID
     */
    Long appendMessage(String sessionId, String userInput, ChatbotReply botReply,
                       MessageOrigin origin, String voiceKey);

    /**
     * LLM 호출 <b>전에</b> 메시지 행을 PROCESSING으로 먼저 커밋한다.
     *
     * <p>이 커밋이 있어야 응답을 기다리는 동안 조회 API가 "처리 중"을 보여줄 수 있다.
     * 하나의 긴 트랜잭션으로 묶으면 커밋 전까지 다른 요청에서 행이 보이지 않는다.
     *
     * @return 생성된 메시지 ID ({@link #settleMessage}에 그대로 넘긴다)
     */
    Long beginMessage(String sessionId, String userInput, MessageOrigin origin, String voiceKey);

    /**
     * LLM 호출이 끝난 뒤 응답과 최종 상태를 기록한다 (PROCESSING → COMPLETED/FAILED).
     * 커밋 후 {@code ChatReplySettledEvent}가 소비돼 푸시가 나간다.
     */
    void settleMessage(Long messageId, ChatbotReply botReply, boolean failed);

    /** 세션에 아직 응답이 확정되지 않은 메시지가 있는지. 처리 중 재전송 차단용. */
    boolean hasReplyInProgress(String sessionId);

    /**
     * 조건을 충족한 일기에 대해 상담 제안(OFFERED) 원장 행을 기록한다. 세션은 만들지 않는다.
     *
     * <p>멱등성은 {@code mind_diary_trigger.voice_id} UNIQUE가 보장한다. 다른 인스턴스가
     * 같은 일기로 먼저 원장을 선점했다면 {@link org.springframework.dao.DataIntegrityViolationException}을
     * 던지며 트랜잭션이 롤백된다. 호출자는 이 예외를 중복으로 해석해 건너뛴다.
     * (예외를 이 메서드 안에서 잡으면 rollback-only로 마킹돼 커밋 시 UnexpectedRollbackException이
     * 발생하므로, 반드시 트랜잭션 밖에서 잡는다.)
     */
    void recordOffer(Voice triggerVoice, String reason);

    /**
     * 제안을 조회하고 소유권 + OFFERED 상태를 검증한다. 수락/거절 진입 시 사용.
     *
     * @throws com.safori.common.exception.GeneralException 없거나(OFFER_NOT_FOUND),
     *         소유자가 아니거나(OFFER_NO_PERMISSION), 이미 응답한 제안(OFFER_NOT_OFFERABLE)
     */
    MindDiaryTrigger getOfferableOffer(Long offerId, User user);

    /**
     * 사용자가 제안을 수락했을 때 호출 — 세션 + 첫 봇 메시지 + 컨텍스트 일기 링크를 저장하고
     * 원장을 ACCEPTED로 전환한다.
     *
     * @param offerId       수락 대상 원장 ID (트랜잭션 안에서 재로딩해 managed 상태로 다룬다)
     * @param triggerVoice  대화를 촉발한 일기 (컨텍스트의 마지막 원소와 동일)
     * @param contextVoices 프롬프트에 사용된 일기 전체. 시간순(오래된 → 최신).
     * @return 생성된 세션 ID
     */
    String createSessionForAcceptedOffer(User user, Long offerId, Voice triggerVoice,
                                         List<Voice> contextVoices, ChatbotReply botReply);

    /** 사용자가 제안을 거절 → 원장을 DECLINED로 전환. 재제안하지 않는다. */
    void declineOffer(Long offerId);


    /**
     * 재분석 등으로 감정이 바뀐 뒤, 사용자의 아직 확정되지 않은 트리거(OFFERED, 또는 0턴
     * ACCEPTED)를 재평가해 더 이상 조건을 만족하지 않으면 철회한다.
     *
     * <ul>
     *   <li>OFFERED → 원장 삭제 (조건이 다시 갖춰지면 나중에 재제안됨)</li>
     *   <li>ACCEPTED이고 사용자 발화 0회 → 세션 + 원장 삭제 (아무도 대화하지 않았으므로)</li>
     *   <li>ACCEPTED이고 발화 1회 이상 → 보존 (상담이 실제로 진행됐으므로 기록으로 남긴다)</li>
     * </ul>
     */
    void withdrawStaleTriggers(Long userId);

    /**
     * 일기가 트리거한 상담 세션과 트리거 원장 행을 삭제한다 (있으면). 일기 삭제 시 호출.
     *
     * <p>일기를 지우면 그 일기를 읽고 만든 대화도 남지 않아야 한다(프라이버시). 세션 삭제가
     * 메시지·컨텍스트 링크를 cascade로 정리한다.
     *
     * <p><b>트리거 원장 행도 여기서 명시적으로 지운다.</b> 원래는 voice 삭제 시
     * {@code mind_diary_trigger.voice_id} FK의 {@code ON DELETE CASCADE}로 함께 정리되길
     * 기대했으나, 이 프로젝트는 {@code ddl-auto: update}라 나중에 추가된 {@code @OnDelete}
     * cascade가 기존 테이블의 FK에 반영되지 않는다(수동 safori.sql로만 보정). 그 FK cascade가
     * 실제 DB에 없으면 voice 삭제가 FK 위반으로 실패한다(삭제 API 500). DB 상태에 의존하지 않도록
     * 원장 행을 앱에서 직접 삭제한다. 일기가 지워지면 어차피 새로 쓰면 새 voice_id라 재트리거 대상이
     * 되므로 원장을 남길 이유도 없다.
     *
     * <p>호출자의 트랜잭션에 참여한다(일기 삭제와 원자적으로 처리되도록).
     */
    void deleteTriggerAndSessionByVoiceId(Long voiceId);
}
