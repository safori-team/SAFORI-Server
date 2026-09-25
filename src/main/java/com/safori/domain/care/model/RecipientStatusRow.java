package com.safori.domain.care.model;

import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareStatusCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 대상자 현황·목록 한 줄. 현재 기록이 없으면 기록 필드가, 담당자가 없으면 담당자 필드가 null이다.
 */
public record RecipientStatusRow(String recipientPublicId,
                                 String name,
                                 LocalDate birthDate,
                                 String recordPublicId,
                                 CareStatusCode statusCode,
                                 String reasonMessage,
                                 CareProcessingStatus processingStatus,
                                 LocalDateTime detectedAt,
                                 String managerAccountUuid,
                                 String managerName) {
}
