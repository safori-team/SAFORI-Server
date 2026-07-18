package com.safori.domain.chatbot.policy;

import java.util.List;

/**
 * 세션 생성 판단 결과.
 *
 * <p>정책은 "만들지 말지"뿐 아니라 "어떤 일기를 근거로 만드는지"까지 함께 반환한다.
 * 조건과 컨텍스트 범위는 같이 바뀌는 값이라(1일 → 3일 연속 → 스트릭 전체) 분리하면
 * 정책 변경 때마다 두 곳을 고쳐야 하기 때문이다.
 *
 * @param create          세션을 생성할지 여부
 * @param reasonCode      판단 근거 코드. 생성된 세션에 기록되어 사후 분석/정책 비교에 쓰인다.
 * @param contextVoiceIds 세션 프롬프트에 넣을 일기 ID. 시간순(오래된 → 최신)이며
 *                        마지막 원소가 트리거 일기다. {@code create=false}면 빈 리스트.
 */
public record SessionTriggerDecision(
        boolean create,
        String reasonCode,
        List<Long> contextVoiceIds
) {
    public static SessionTriggerDecision skip(String reasonCode) {
        return new SessionTriggerDecision(false, reasonCode, List.of());
    }

    public static SessionTriggerDecision create(String reasonCode, List<Long> contextVoiceIds) {
        return new SessionTriggerDecision(true, reasonCode, List.copyOf(contextVoiceIds));
    }

    /** 트리거 일기 = 컨텍스트의 마지막 원소. */
    public Long triggerVoiceId() {
        return contextVoiceIds.isEmpty() ? null : contextVoiceIds.get(contextVoiceIds.size() - 1);
    }
}
