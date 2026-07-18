package com.safori.domain.chatbot.policy;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.emotion.service.EmotionResolver;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionReportAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일기 1건이 상담 트리거 조건을 만족하는지 판단한다.
 *
 * <p>스케줄러(제안 생성)와 수락 API(제안 유효성 재확인) 양쪽이 같은 기준으로 판단하도록
 * effectiveEmotion 산출 + {@link SessionTriggerPolicy} 호출을 한곳에 모은다.
 */
@Component
@RequiredArgsConstructor
public class MindDiaryTriggerEvaluator {

    private final VoiceAdaptor voiceAdaptor;
    private final UserAdaptor userAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionReportAdaptor voiceEmotionReportAdaptor;
    private final SessionTriggerPolicyResolver policyResolver;

    /** 로딩된 엔티티까지 함께 반환해 호출자가 재조회하지 않도록 한다. */
    public record Evaluation(Voice voice, User user, SessionTriggerDecision decision) {
        public boolean shouldCreate() {
            return decision.create();
        }
    }

    public Evaluation evaluate(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        User user = userAdaptor.queryUserById(voice.getUser().getId());

        VoiceComposite composite = voiceCompositeAdaptor.findByVoiceId(voiceId).orElse(null);
        SessionTriggerContext ctx = new SessionTriggerContext(
                user.getId(), voiceId, voice.getCreatedDate().toLocalDate(),
                effectiveEmotion(voice, user, composite));

        SessionTriggerDecision decision = policyResolver.current().decide(ctx);
        return new Evaluation(voice, user, decision);
    }

    /** 사용자 신고가 있으면 신고 감정, 없으면 AI 분석 감정. */
    private EmotionType effectiveEmotion(Voice voice, User user, VoiceComposite composite) {
        EmotionType aiTopEmotion = composite == null ? null : composite.getTopEmotion();
        EmotionType reportedEmotion = voiceEmotionReportAdaptor
                .findByVoiceIdAndUsername(voice.getId(), user.getUsername())
                .map(VoiceEmotionReport::getReportedEmotion)
                .orElse(null);
        return EmotionResolver.effectiveTopEmotion(aiTopEmotion, reportedEmotion);
    }
}
