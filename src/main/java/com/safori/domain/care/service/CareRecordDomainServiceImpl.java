package com.safori.domain.care.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.repository.CareRecordRepository;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.safori.domain.care.exception.CareHandler.RECORD_NOT_FOUND;
import static com.safori.domain.care.exception.CareHandler.RECORD_NOT_PROCESSABLE;

@Transactional
@DomainService
@RequiredArgsConstructor
public class CareRecordDomainServiceImpl implements CareRecordDomainService {

    private final CareRecipientRepository recipientRepository;
    private final CareRecordRepository recordRepository;

    @Override
    public CareRecord raise(CareRecipient recipient, CareReasonType reasonType, String reasonMessage,
                            LocalDateTime detectedAt) {
        CareRecipient locked = lock(recipient);
        CareRecord current = locked.getCurrentRecord();
        if (current != null && current.getReasonType() == reasonType && !reasonType.repeatable()) {
            return current;
        }
        CareRecord record = recordRepository.save(
                CareRecord.detect(locked, reasonType, reasonMessage, detectedAt));
        locked.receive(record);
        return record;
    }

    @Override
    public CareRecord process(CareRecipient recipient, String recordPublicId, CareProcessingStatus status,
                              OrganizationMember processedBy) {
        CareRecipient locked = lock(recipient);
        CareRecord record = recordRepository.findByRecipientAndPublicId(locked, recordPublicId)
                .orElseThrow(() -> RECORD_NOT_FOUND);
        if (!locked.process(record, status, processedBy, LocalDateTime.now())) {
            throw RECORD_NOT_PROCESSABLE;
        }
        return record;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastCompletedAt(CareRecipient recipient, CareReasonType reasonType) {
        return Optional.ofNullable(recordRepository.findLastCompletedAt(recipient, reasonType));
    }

    private CareRecipient lock(CareRecipient recipient) {
        return recipientRepository.findByIdForUpdate(recipient.getId())
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 어르신입니다: " + recipient.getId()));
    }
}
