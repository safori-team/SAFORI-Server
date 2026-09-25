package com.safori.api.recipient.service;

import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.adaptor.CareRecipientAdaptor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.exception.CareHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 경로의 {@code careRecipientId}(public_id)를 요청한 구성원 기관의 대상자로 찾는다. 없거나 다른 기관이면 4454.
 */
@Component
@RequiredArgsConstructor
public class OrganizationRecipients {

    private final CareRecipientAdaptor recipientAdaptor;

    public CareRecipient get(BackofficeActor actor, String careRecipientId) {
        return recipientAdaptor.findByPublicId(careRecipientId)
                .filter(recipient -> recipient.getOrganization().getId().equals(actor.organizationId()))
                .orElseThrow(() -> CareHandler.RECIPIENT_NOT_FOUND);
    }
}
