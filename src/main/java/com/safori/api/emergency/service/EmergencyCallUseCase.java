package com.safori.api.emergency.service;

import com.safori.api.emergency.dto.EmergencyCallResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareRecipientStatus;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.service.CareRecordDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 어르신이 앱에서 긴급 전화(119)를 걸 때 복지관에 즉시 확인(도움 요청)을 올린다.
 * 전화 걸기는 앱이 하고 서버는 통화 여부를 모른다 — "전화 앱 열기"를 누른 시점을 요청으로 본다.
 * 기관에 등록된 활성 대상자가 아니면 아무것도 올리지 않는다(전화를 막지 않도록 오류로 응답하지 않는다).
 */
@UseCase
@RequiredArgsConstructor
public class EmergencyCallUseCase {

    static final String REASON_MESSAGE = "119에 SOS 요청을 했어요.";

    private final UserAdaptor userAdaptor;
    private final CareRecipientRepository recipientRepository;
    private final CareRecordDomainService recordDomainService;

    @Transactional
    public EmergencyCallResponse execute(String username) {
        Long userId = userAdaptor.queryUserByUsername(username).getId();
        boolean notified = recipientRepository.findByUserIdAndStatus(userId, CareRecipientStatus.ACTIVE)
                .filter(recipient -> recipient.getOrganization().isActive())
                .map(recipient -> recordDomainService.raise(recipient, CareReasonType.HELP_REQUEST, REASON_MESSAGE,
                        LocalDateTime.now()))
                .isPresent();
        return new EmergencyCallResponse(notified);
    }
}
