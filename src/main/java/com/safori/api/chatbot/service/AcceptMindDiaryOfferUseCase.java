package com.safori.api.chatbot.service;

import com.safori.api.chatbot.dto.CreateSessionResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.chatbot.entity.MindDiaryTrigger;
import com.safori.domain.chatbot.exception.ChatbotHandler;
import com.safori.domain.chatbot.model.ChatbotReply;
import com.safori.domain.chatbot.model.MindDiaryEntry;
import com.safori.domain.chatbot.policy.MindDiaryTriggerEvaluator;
import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.chatbot.service.MindDiaryContextAssembler;
import com.safori.domain.user.UserHonorific;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.infra.ai.gemini.GeminiChatbotClient;
import com.safori.infra.ai.gemini.prompts.MindDiaryPrompt;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 사용자가 모달에서 상담 제안을 수락 → 세션을 생성한다.
 *
 * <p>트랜잭션을 분리한다 (LLM 호출이 DB 커넥션을 점유하지 않도록):
 * <ol>
 *   <li>제안 검증 + 조건 재확인 + 컨텍스트 조립 (readOnly 경계)</li>
 *   <li>(트랜잭션 밖) Gemini 첫 메시지 생성</li>
 *   <li>세션 저장 + 원장 ACCEPTED 전환 (Transactional)</li>
 * </ol>
 *
 * <p>제안 시점과 수락 시점 사이에 재분석 등으로 조건이 깨졌으면(정책 재평가 실패)
 * {@code OFFER_EXPIRED}로 거절한다 — 이미 조건을 벗어난 상담을 새로 시작하지 않는다.
 */
@UseCase
@RequiredArgsConstructor
public class AcceptMindDiaryOfferUseCase {

    private final ChatbotDomainService chatbotDomainService;
    private final MindDiaryTriggerEvaluator evaluator;
    private final MindDiaryContextAssembler assembler;
    private final UserAdaptor userAdaptor;
    private final VoiceAdaptor voiceAdaptor;
    private final GeminiChatbotClient geminiChatbotClient;

    public CreateSessionResponse execute(String username, Long offerId) {
        // 1) 제안 검증 (소유권 + OFFERED)
        MindDiaryTrigger offer = chatbotDomainService.getOfferableOffer(
                offerId, userAdaptor.queryUserByUsername(username));

        // 2) 조건 재확인 — 제안 이후 재분석 등으로 스트릭이 깨졌으면 만료 처리
        MindDiaryTriggerEvaluator.Evaluation eval = evaluator.evaluate(offer.getVoice().getId());
        if (!eval.shouldCreate()) {
            throw ChatbotHandler.OFFER_EXPIRED;
        }
        User user = eval.user();

        List<Voice> contextVoices = eval.decision().contextVoiceIds().stream()
                .map(voiceAdaptor::queryById)
                .toList();
        List<MindDiaryEntry> entries = assembler.assemble(contextVoices);

        // 3) LLM 첫 메시지 (트랜잭션 밖)
        String address = UserHonorific.of(user);
        ChatbotReply reply = geminiChatbotClient
                .generate(MindDiaryPrompt.build(address, entries))
                .toReply();

        // 4) 세션 저장 + 원장 ACCEPTED
        String sessionId = chatbotDomainService.createSessionForAcceptedOffer(
                user, offerId, eval.voice(), contextVoices, reply);
        return new CreateSessionResponse(sessionId);
    }
}
