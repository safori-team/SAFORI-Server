package com.safori.common.event;

/**
 * 마음일기 트리거로 상담 제안(OFFERED)이 기록됐을 때 발행된다.
 *
 * <p>{@code recordOffer} 트랜잭션 안에서 발행되며, 소비자는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 커밋 이후에만 처리한다 —
 * 중복 제안으로 트랜잭션이 롤백되면 푸시가 나가지 않게 하기 위함이다.
 *
 * @param userId  제안 대상 사용자 ID (푸시 수신자)
 * @param offerId 생성된 {@code mind_diary_trigger} ID (딥링크 페이로드용)
 */
public record MindDiaryOfferedEvent(Long userId, Long offerId) {
}
