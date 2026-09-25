package com.safori.domain.care.service;

import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.organization.entity.OrganizationMember;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 대상자 기록(상태 코드·처리 상태). 같은 대상자에 대한 기록 추가·처리는 대상자 행을 잠가 직렬화한다.
 */
public interface CareRecordDomainService {

    /**
     * 감지한 확인 사유를 기록한다. 판정은 호출하는 쪽({@code CareStatusDetector})이 하고, 이 메서드는
     * 덮어쓰기 규칙(같거나 높으면 현재 기록 교체, 낮으면 흡수)을 적용한다.
     * 같은 사유가 이미 현재 기록이면(반복 가능한 사유 제외) 처리 중인 기록을 흔들지 않도록 새로 만들지 않는다.
     *
     * @return 새 기록, 또는 무시했으면 기존 현재 기록
     */
    CareRecord raise(CareRecipient recipient, CareReasonType reasonType, String reasonMessage,
                     LocalDateTime detectedAt);

    /** 이 사유를 마지막으로 완료한 시각. 판정은 이 시각 이후 데이터만 센다(완료하면 집계 초기화). */
    Optional<LocalDateTime> lastCompletedAt(CareRecipient recipient, CareReasonType reasonType);

    /** 현재 기록의 처리 상태를 바꾼다. 완료하면 상태 코드가 X가 된다. */
    CareRecord process(CareRecipient recipient, String recordPublicId, CareProcessingStatus status,
                       OrganizationMember processedBy);
}
