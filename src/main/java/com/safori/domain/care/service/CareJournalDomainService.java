package com.safori.domain.care.service;

import com.safori.domain.care.entity.CareJournal;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.model.JournalSelectionInput;
import com.safori.domain.organization.entity.OrganizationMember;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 담당자 일지. 선택 항목은 일지 폼(섹션·항목) 규칙으로 검증한다.
 */
public interface CareJournalDomainService {

    /**
     * 일지를 쓴다. 작성 당시 확인 필요도·처리 상태를 스냅샷으로 남기고, 처리 상태는 바꾸지 않는다.
     *
     * @throws com.safori.common.exception.GeneralException 4457 — 선택 항목이 폼 규칙에 맞지 않을 때
     */
    CareJournal write(CareRecipient recipient, OrganizationMember writer, LocalDateTime confirmedAt,
                      List<JournalSelectionInput> selections, String memo, boolean guardianVisible);
}
