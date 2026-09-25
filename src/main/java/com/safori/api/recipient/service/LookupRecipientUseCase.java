package com.safori.api.recipient.service;

import com.safori.api.recipient.dto.RecipientLookupResponse;
import com.safori.common.annotation.UseCase;
import com.safori.common.util.Masking;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디를 정확히 입력했을 때만 어르신을 찾는다(검색·부분 일치 없음). 없으면 4052.
 */
@UseCase
@RequiredArgsConstructor
public class LookupRecipientUseCase {

    private final UserAdaptor userAdaptor;
    private final CareRecipientRepository recipientRepository;

    @Transactional(readOnly = true)
    public RecipientLookupResponse execute(String loginId) {
        User user = userAdaptor.queryUserByUsername(loginId);
        return new RecipientLookupResponse(
                user.getUsername(),
                Masking.name(user.getName()),
                Masking.phone(user.getPhone()),
                Masking.birthDate(user.getBirthDate()),
                recipientRepository.existsByUserId(user.getId()));
    }
}
