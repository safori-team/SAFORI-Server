package com.safori.domain.care.model;

import com.safori.domain.care.entity.CareStatusCode;

import java.time.LocalDateTime;

/**
 * 일지 목록 한 줄. 확인 방식·결과는 선택 항목에서 따로 읽는다.
 *
 * @param statusCode 작성 당시 확인 필요도. 없었으면 null
 */
public record JournalListRow(Long id,
                             String journalPublicId,
                             String recipientPublicId,
                             String recipientName,
                             LocalDateTime confirmedAt,
                             CareStatusCode statusCode,
                             String writerName) {
}
