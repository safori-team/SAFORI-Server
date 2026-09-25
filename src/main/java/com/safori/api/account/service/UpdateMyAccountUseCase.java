package com.safori.api.account.service;

import com.safori.api.account.dto.MyAccountResponse;
import com.safori.api.account.dto.UpdateMyAccountRequest;
import com.safori.common.annotation.UseCase;
import com.safori.common.service.RefreshTokenService;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 백오피스 계정이 자기 정보를 고친다. 관리자는 이름·연락처·아이디·비밀번호를, 담당자·보호자는 비밀번호만
 * 바꿀 수 있다(연락처는 기관 관리자에게 요청). 아이디를 바꾸면 리프레시 토큰도 새 아이디로 옮겨 로그인이 유지된다.
 */
@UseCase
@RequiredArgsConstructor
public class UpdateMyAccountUseCase {

    private final OrganizationMemberRepository memberRepository;
    private final AccessGroupDomainService accessGroupDomainService;
    private final BackofficeAccountDomainService accountDomainService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public MyAccountResponse execute(BackofficeActor actor, UpdateMyAccountRequest request) {
        OrganizationMember member = memberRepository.findForAuthorization(actor.organizationMemberId()).orElseThrow();
        BackofficeAccount account = member.getAccount();
        boolean admin = accessGroupDomainService.primaryTemplateOf(member)
                .filter(RoleTemplateCode.ORG_ADMIN::equals).isPresent();
        boolean loginIdChanged = request.loginId() != null && !request.loginId().equals(account.getLoginId());
        if (!admin && (request.name() != null || request.phone() != null || loginIdChanged)) {
            throw AccountHandler.PROFILE_NOT_EDITABLE;
        }

        if (request.name() != null || request.phone() != null) {
            account = accountDomainService.changeProfile(account,
                    request.name() != null ? request.name() : account.getName(),
                    request.phone() != null ? request.phone() : account.getPhone());
        }
        String oldLoginId = account.getLoginId();
        if (loginIdChanged) {
            account = accountDomainService.changeLoginId(account, request.loginId());
        }
        if (request.password() != null) {
            account = accountDomainService.changePassword(account, request.password());
        }
        MyAccountResponse response = new MyAccountResponse(account.getLoginId(), account.getName(), account.getPhone());
        if (loginIdChanged) {
            // 영속성 컨텍스트를 비우므로 모든 변경 뒤에 한다.
            refreshTokenService.renameOwner(oldLoginId, request.loginId());
        }
        return response;
    }
}
