package com.safori.api.recipient.service;

import com.safori.api.recipient.dto.RecipientDetailResponse;
import com.safori.api.recipient.dto.UpdateRecipientRequest;
import com.safori.common.annotation.UseCase;
import com.safori.common.service.RefreshTokenService;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.exception.CareHandler;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.user.service.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 대상자(어르신 앱 계정)의 기본 정보·아이디·비밀번호와 기관 이용 상태를 고친다.
 * 아이디를 바꾸면 어르신 앱의 리프레시 토큰도 새 아이디로 옮겨 로그인이 유지된다.
 */
@UseCase
@RequiredArgsConstructor
public class UpdateRecipientUseCase {

    private final OrganizationRecipients organizationRecipients;
    private final UserAdaptor userAdaptor;
    private final UserDomainService userDomainService;
    private final RefreshTokenService refreshTokenService;
    private final GetRecipientUseCase getRecipientUseCase;

    @Transactional
    public RecipientDetailResponse execute(BackofficeActor actor, String careRecipientId,
                                           UpdateRecipientRequest request) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        if (recipient.getUserId() == null) {
            // 앱 계정 없이 등록된 대상자는 지금 등록 흐름으로는 생기지 않는다.
            throw CareHandler.RECIPIENT_NOT_FOUND;
        }
        User user = userAdaptor.queryUserById(recipient.getUserId());
        String oldUsername = user.getUsername();

        userDomainService.changeProfile(user, request.name(), request.phone(), request.birthDate());
        userDomainService.changeUsername(user, request.loginId());
        if (request.password() != null) {
            userDomainService.changePassword(user, request.password());
        }
        if (request.active() && !recipient.isActive()) {
            recipient.activate();
        } else if (!request.active() && recipient.isActive()) {
            recipient.deactivate();
        }
        if (!oldUsername.equals(request.loginId())) {
            // 영속성 컨텍스트를 비우므로 모든 변경 뒤에 한다.
            // ponytail: 이미 발급된 액세스 토큰(최대 30분)은 옛 아이디를 담고 있어 만료 전까지 401이 나고 재발급으로 이어진다.
            // 그 사이 옛 아이디로 새로 가입하면 그 토큰이 새 계정으로 풀린다. 문제가 되면 앱 토큰 subject를 user_uuid로 바꾼다.
            refreshTokenService.renameOwner(oldUsername, request.loginId());
        }
        return getRecipientUseCase.execute(actor, careRecipientId);
    }
}
