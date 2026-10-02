package com.safori.common.event;

/**
 * 대상자의 상태 코드가 '즉시 확인'으로 올라갔을 때 발행된다(이미 즉시 확인이면 발행하지 않는다).
 *
 * <p>기록을 올리는 트랜잭션 안에서 발행되며, 소비자는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 커밋 이후에만 처리한다.
 *
 * @param recordId 즉시 확인으로 올린 기록({@code care_record.record_id})
 */
public record CareUrgentEnteredEvent(Long recordId) {
}
