package com.safori.domain.care.service;

import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.organization.entity.OrganizationMember;

import java.time.LocalDateTime;

/**
 * 대상자 기록(상태 코드·처리 상태). 같은 대상자에 대한 기록 추가·처리는 대상자 행을 잠가 직렬화한다.
 */
public interface CareRecordDomainService {

    /**
     * 시스템이 감지한 확인 사유를 기록한다. 상태 코드 판정 규칙은 호출하는 쪽이 정하고, 이 메서드는
     * 덮어쓰기 규칙(같거나 높으면 현재 기록 교체, 낮으면 흡수)만 적용한다.
     */
    CareRecord raise(CareRecipient recipient, CareStatusCode statusCode, String reasonType, String reasonMessage,
                     LocalDateTime detectedAt);

    /** 현재 기록의 처리 상태를 바꾼다. 완료하면 상태 코드가 X가 된다. */
    CareRecord process(CareRecipient recipient, String recordPublicId, CareProcessingStatus status,
                       OrganizationMember processedBy);
}
