package com.safori.api.recipient.service;

import com.safori.api.recipient.dto.RegisterRecipientResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어르신을 요청한 구성원의 기관(토큰 기준)에 대상자로 등록한다. 요청 값으로 기관을 받지 않는다.
 * 어르신은 한 기관에만 등록되며, 이미 등록돼 있으면 4450.
 */
@UseCase
@RequiredArgsConstructor
public class RegisterRecipientUseCase {

    private final UserAdaptor userAdaptor;
    private final OrganizationRepository organizationRepository;
    private final CareRelationDomainService careRelationDomainService;

    @Transactional
    public RegisterRecipientResponse execute(BackofficeActor actor, String loginId) {
        User user = userAdaptor.queryUserByUsername(loginId);
        CareRecipient recipient = careRelationDomainService.registerRecipient(
                organizationRepository.getReferenceById(actor.organizationId()), user.getId());
        return new RegisterRecipientResponse(recipient.getPublicId());
    }
}
