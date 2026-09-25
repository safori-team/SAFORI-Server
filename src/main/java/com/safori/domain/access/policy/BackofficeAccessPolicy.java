package com.safori.domain.access.policy;

import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.care.adaptor.CareRecipientAdaptor;
import com.safori.domain.care.adaptor.CareRelationAdaptor;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.organization.adaptor.OrganizationMemberAdaptor;
import com.safori.domain.organization.entity.OrganizationMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 백오피스 인가 판정의 단일 진입점.
 *
 * <p>아래 네 조건을 모두 만족해야 허용한다. 관리자라고 검사를 건너뛰지 않는다.
 * <ol>
 *   <li>계정·기관·멤버십이 모두 활성</li>
 *   <li>최종 권한(그룹 상속 역할 ∪ 개인 역할)에 요청 권한이 있음</li>
 *   <li>대상이 본인 기관 소속 — 기관 관리자도 다른 기관은 다룰 수 없다</li>
 *   <li>그 권한을 준 역할의 데이터 범위 안 — 기관 전체 / 현재 본인 배정 / 현재 본인 연결</li>
 * </ol>
 *
 * <p>매 호출마다 DB의 현재 상태로 판정한다. 배정 종료·연결 해제·권한 회수·정지가 다음 판정부터 반영된다.
 * 변경 작업은 메서드 진입 전 검사와 별개로 같은 트랜잭션 안에서 이 판정을 다시 호출해 재검증한다.
 *
 * <p>메서드 보안에서는 빈 이름으로 호출한다. 권한 코드는 {@link PermissionCode} 이름 문자열이다.
 * <pre>{@code
 * @PreAuthorize("@backofficeAccessPolicy.canAccessRecipient(authentication, 'RECIPIENT_READ', #recipientId)")
 * public RecipientDetail execute(Long recipientId) { ... }
 * }</pre>
 */
@Slf4j
@Component("backofficeAccessPolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackofficeAccessPolicy {

    private final OrganizationMemberAdaptor organizationMemberAdaptor;
    private final CareRecipientAdaptor careRecipientAdaptor;
    private final CareRelationAdaptor careRelationAdaptor;
    private final EffectivePermissionResolver permissionResolver;

    // -- 메서드 보안(SpEL)용: Authentication + 권한 코드 문자열 ------------------------------

    /** 범위와 무관하게 권한을 갖고 있는지. URL·기능 단위의 거친 검사용이다. */
    public boolean hasPermission(Authentication authentication, String permission) {
        return resolve(authentication, permission)
                .map(context -> hasPermission(context.memberId(), context.permission()))
                .orElse(false);
    }

    /** 기관 단위 작업(대상자 등록, 담당자별 배정 조회 등). 기관 전체 범위로 받은 권한만 인정한다. */
    public boolean canAccessOrganization(Authentication authentication, String permission, Long organizationId) {
        return resolve(authentication, permission)
                .map(context -> canAccessOrganization(context.memberId(), context.permission(), organizationId))
                .orElse(false);
    }

    /** 특정 어르신에 대한 작업. */
    public boolean canAccessRecipient(Authentication authentication, String permission, Long recipientId) {
        return resolve(authentication, permission)
                .map(context -> canAccessRecipient(context.memberId(), context.permission(), recipientId))
                .orElse(false);
    }

    // -- 코드용: 구성원 ID + PermissionCode ------------------------------------------------

    public boolean hasPermission(Long organizationMemberId, PermissionCode permission) {
        return loadActiveMember(organizationMemberId)
                .map(member -> permissionResolver.resolve(member).has(permission))
                .orElse(false);
    }

    public boolean canAccessOrganization(Long organizationMemberId, PermissionCode permission, Long organizationId) {
        return loadActiveMember(organizationMemberId)
                .filter(member -> Objects.equals(member.getOrganization().getId(), organizationId))
                .map(member -> permissionResolver.resolve(member).scopesOf(permission)
                        .contains(DataScope.ORGANIZATION))
                .orElse(false);
    }

    public boolean canAccessRecipient(Long organizationMemberId, PermissionCode permission, Long recipientId) {
        if (recipientId == null) {
            return false;
        }
        return loadActiveMember(organizationMemberId)
                .flatMap(member -> careRecipientAdaptor.findById(recipientId)
                        .map(recipient -> isAccessible(member, permission, recipient)))
                .orElse(false);
    }

    /** URL 경로 변수처럼 외부 식별자({@code care_recipient.public_id})로 들어온 어르신에 대한 작업. */
    public boolean canAccessRecipientByPublicId(Long organizationMemberId, PermissionCode permission,
                                                String recipientPublicId) {
        if (recipientPublicId == null) {
            return false;
        }
        return loadActiveMember(organizationMemberId)
                .flatMap(member -> careRecipientAdaptor.findByPublicId(recipientPublicId)
                        .map(recipient -> isAccessible(member, permission, recipient)))
                .orElse(false);
    }

    /**
     * 목록·검색·count·export 조회에 걸 범위 조건.
     * {@code CareRecipientAdaptor#queryAccessible(RecipientAccessScope, Pageable)}에 그대로 넘긴다.
     */
    public RecipientAccessScope recipientScope(Long organizationMemberId, PermissionCode permission) {
        return loadActiveMember(organizationMemberId)
                .map(member -> new RecipientAccessScope(
                        member.getOrganization().getId(),
                        member.getId(),
                        permissionResolver.resolve(member).scopesOf(permission)))
                .orElseGet(RecipientAccessScope::none);
    }

    // -- 내부 판정 ----------------------------------------------------------------------

    private boolean isAccessible(OrganizationMember member, PermissionCode permission, CareRecipient recipient) {
        boolean sameOrganization = Objects.equals(
                recipient.getOrganization().getId(), member.getOrganization().getId());
        if (!sameOrganization || !recipient.isActive()) {
            return false;
        }
        Set<DataScope> scopes = permissionResolver.resolve(member).scopesOf(permission);
        return !scopes.isEmpty() && isWithinScope(member, recipient, scopes);
    }

    private boolean isWithinScope(OrganizationMember member, CareRecipient recipient, Set<DataScope> scopes) {
        if (scopes.contains(DataScope.ORGANIZATION)) {
            return true;
        }
        if (scopes.contains(DataScope.ASSIGNED_RECIPIENT)
                && careRelationAdaptor.existsActiveAssignment(recipient, member)) {
            return true;
        }
        return scopes.contains(DataScope.LINKED_RECIPIENT)
                && careRelationAdaptor.existsActiveGuardianLink(recipient, member);
    }

    private Optional<OrganizationMember> loadActiveMember(Long organizationMemberId) {
        if (organizationMemberId == null) {
            return Optional.empty();
        }
        return organizationMemberAdaptor.findForAuthorization(organizationMemberId)
                .filter(OrganizationMember::isActiveActor);
    }

    private Optional<ActorPermission> resolve(Authentication authentication, String permission) {
        Optional<PermissionCode> code = PermissionCode.find(permission);
        if (code.isEmpty()) {
            log.warn("알 수 없는 백오피스 권한 코드라 거부합니다: {}", permission);
            return Optional.empty();
        }
        return BackofficeActor.from(authentication)
                .map(actor -> new ActorPermission(actor.organizationMemberId(), code.get()));
    }

    private record ActorPermission(Long memberId, PermissionCode permission) {
    }
}
