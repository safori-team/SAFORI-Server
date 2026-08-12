package com.safori.common.event;

/**
 * 도란이 응답이 COMPLETED 또는 FAILED로 확정됐을 때 발행된다.
 *
 * <p>{@code settleMessage} 트랜잭션 안에서 발행되며, 소비자는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 커밋 이후에만 처리한다 —
 * 상태 갱신이 롤백되면 푸시가 나가지 않게 하기 위함이다.
 *
 * @param userId    푸시 수신자
 * @param sessionId 딥링크 대상 세션 (앱이 이 세션 상세로 이동한다)
 * @param messageId 확정된 메시지 ID
 * @param failed    실패로 확정됐는지 (푸시 문구·타입 분기)
 */
public record ChatReplySettledEvent(Long userId, String sessionId, Long messageId, boolean failed) {
}
