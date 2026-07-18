package com.safori.domain.chatbot.policy;

import com.safori.domain.chatbot.policy.SessionTriggerProperties.PolicyType;

/**
 * 마음일기로부터 챗봇 세션을 생성할지 판단하는 정책.
 *
 * <p>새 규칙이 필요하면 이 인터페이스 구현체를 추가하고
 * {@code safori.chatbot.session-trigger.policy} 값을 바꾼다. 스케줄러·프롬프트·저장 로직은
 * 그대로 둔다.
 */
public interface SessionTriggerPolicy {

    /** 이 구현체가 담당하는 {@link PolicyType}. 설정값으로 빈을 선택하는 키. */
    PolicyType type();

    /**
     * 후보 일기 1건에 대해 세션 생성 여부와 컨텍스트 일기 범위를 결정한다.
     * 부수효과 없이 조회만 수행해야 한다 — 스케줄러가 매 tick 재평가하기 때문이다.
     */
    SessionTriggerDecision decide(SessionTriggerContext context);
}
