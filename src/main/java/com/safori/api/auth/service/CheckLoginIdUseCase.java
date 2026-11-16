package com.safori.api.auth.service;

import com.safori.api.auth.dto.CheckLoginIdResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.user.adaptor.UserAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 엔드포인트가 하나라 아이디는 어르신(users)·백오피스(backoffice_account) 계정을 통틀어 유일하다.
 * 어르신 회원가입과 담당자·보호자 등록 폼이 같이 쓴다. 최종 보장은 가입·등록 시점의 중복 검사다.
 */
@UseCase
@RequiredArgsConstructor
public class CheckLoginIdUseCase {

    private final UserAdaptor userAdaptor;
    private final BackofficeAccountRepository backofficeAccountRepository;

    @Transactional(readOnly = true)
    public CheckLoginIdResponse execute(String loginId) {
        boolean taken = userAdaptor.existsByUsername(loginId) || backofficeAccountRepository.existsByLoginId(loginId);
        return new CheckLoginIdResponse(!taken);
    }
}
