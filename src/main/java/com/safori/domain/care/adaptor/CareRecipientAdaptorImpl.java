package com.safori.domain.care.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.access.policy.RecipientAccessScope;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@Adaptor
@RequiredArgsConstructor
public class CareRecipientAdaptorImpl implements CareRecipientAdaptor {

    private final CareRecipientRepository careRecipientRepository;

    @Override
    public Optional<CareRecipient> findById(Long recipientId) {
        return careRecipientRepository.findById(recipientId);
    }

    @Override
    public Optional<CareRecipient> findByPublicId(String publicId) {
        return careRecipientRepository.findByPublicId(publicId);
    }

    @Override
    public Page<CareRecipient> queryAccessible(RecipientAccessScope scope, Pageable pageable) {
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }
        return careRecipientRepository.findAccessible(
                scope.organizationId(), scope.organizationMemberId(),
                scope.organizationWide(), scope.includesAssigned(), scope.includesLinked(), pageable);
    }
}
