package com.safori.common.event;

/**
 * 어르신이 기본 상담 후 '조금 더 이야기하기'(세션 연장)를 선택했을 때 발행된다.
 *
 * <p>{@code extendSession} 트랜잭션 안에서 발행되며, 소비자는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 커밋 이후에만 처리한다.
 *
 * @param userId 어르신 앱 계정
 */
public record CounselExtendedEvent(Long userId) {
}
