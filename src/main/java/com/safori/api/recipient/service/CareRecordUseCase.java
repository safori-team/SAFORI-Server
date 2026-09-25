package com.safori.api.recipient.service;

import com.safori.api.recipient.dto.CareRecordResponse;
import com.safori.api.worker.service.OrganizationWorkers;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.care.repository.CareRecordRepository;
import com.safori.domain.care.service.CareRecordDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 기록 상세·처리 상태 변경, 그리고 판정 규칙이 붙기 전 개발용 기록 추가.
 */
@UseCase
@RequiredArgsConstructor
public class CareRecordUseCase {

    private final OrganizationRecipients organizationRecipients;
    private final OrganizationWorkers organizationWorkers;
    private final CareRecordRepository recordRepository;
    private final CareRecordDomainService recordDomainService;

    @Transactional(readOnly = true)
    public CareRecordResponse get(BackofficeActor actor, String careRecipientId, String recordId) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        return recordRepository.findByRecipientAndPublicId(recipient, recordId)
                .map(CareRecordResponse::of)
                .orElseThrow(() -> CareHandler.RECORD_NOT_FOUND);
    }

    @Transactional
    public CareRecordResponse changeProcessingStatus(BackofficeActor actor, String careRecipientId, String recordId,
                                                     CareProcessingStatus status) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        return CareRecordResponse.of(
                recordDomainService.process(recipient, recordId, status, organizationWorkers.actorOf(actor)));
    }

    @Transactional
    public CareRecordResponse raise(BackofficeActor actor, String careRecipientId, CareStatusCode statusCode,
                                    String reasonType, String reasonMessage, LocalDateTime detectedAt) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        return CareRecordResponse.of(recordDomainService.raise(recipient, statusCode, reasonType, reasonMessage,
                detectedAt == null ? LocalDateTime.now() : detectedAt));
    }
}
