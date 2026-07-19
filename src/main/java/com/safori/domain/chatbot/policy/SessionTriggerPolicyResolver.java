package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.policy.SessionTriggerProperties.PolicyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 설정값({@code safori.chatbot.session-trigger.policy})에 맞는 {@link SessionTriggerPolicy}를 고른다.
 * 새 정책은 {@link SessionTriggerPolicy} 구현체를 빈으로 등록하기만 하면 자동 인식된다.
 */
@Slf4j
@Component
public class SessionTriggerPolicyResolver {

    private final Map<PolicyType, SessionTriggerPolicy> policies = new EnumMap<>(PolicyType.class);
    private final SessionTriggerProperties props;

    public SessionTriggerPolicyResolver(List<SessionTriggerPolicy> policies,
                                        SessionTriggerProperties props) {
        this.props = props;
        for (SessionTriggerPolicy p : policies) {
            SessionTriggerPolicy prev = this.policies.put(p.type(), p);
            if (prev != null) {
                throw new IllegalStateException(
                        "SessionTriggerPolicy 중복 등록: type=" + p.type()
                                + " (" + prev.getClass().getSimpleName()
                                + ", " + p.getClass().getSimpleName() + ")");
            }
        }
        log.info("SessionTriggerPolicy 등록: {} / 활성 정책: {}",
                this.policies.keySet(), props.getPolicy());
    }

    public SessionTriggerPolicy current() {
        SessionTriggerPolicy policy = policies.get(props.getPolicy());
        if (policy == null) {
            throw new IllegalStateException(
                    "설정된 SessionTriggerPolicy 구현체 없음: " + props.getPolicy());
        }
        return policy;
    }
}
