package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.policy.SessionTriggerProperties.PolicyType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 마음일기 1건마다 세션 1개를 생성하던 구 정책.
 * {@code policy: ALWAYS}로 되돌리면 즉시 복귀할 수 있도록 남겨둔다.
 */
@Component
public class AlwaysCreatePolicy implements SessionTriggerPolicy {

    @Override
    public PolicyType type() {
        return PolicyType.ALWAYS;
    }

    @Override
    public SessionTriggerDecision decide(SessionTriggerContext context) {
        return SessionTriggerDecision.create("ALWAYS", List.of(context.voiceId()));
    }
}
