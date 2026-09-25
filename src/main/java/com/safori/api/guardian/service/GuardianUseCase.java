package com.safori.api.guardian.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.guardian.dto.GuardianDetailResponse;
import com.safori.api.guardian.dto.GuardianListResponse;
import com.safori.api.guardian.dto.GuardianStatusFilter;
import com.safori.api.guardian.dto.LinkGuardianRequest;
import com.safori.api.guardian.dto.RegisterGuardianRequest;
import com.safori.api.guardian.dto.UpdateGuardianRequest;
import com.safori.api.recipient.service.OrganizationRecipients;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.access.policy.BackofficeActor;
import com.safori.domain.access.service.AccessGroupDomainService;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.entity.BackofficeAccountStatus;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.GuardianRecipientLink;
import com.safori.domain.care.entity.GuardianRelation;
import com.safori.domain.care.repository.GuardianRecipientLinkRepository;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.model.GuardianCounts;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 보호자 등록·목록·상세·수정과 대상자 연결·해제. 보호자는 대상자 한 명에만 연결되고, 관계는 연결에 둔다.
 * 경로의 {@code guardianId}(계정 UUID)가 요청한 구성원 기관의 보호자가 아니면 없는 구성원으로 본다(4307).
 */
@UseCase
@RequiredArgsConstructor
public class GuardianUseCase {

    private final BackofficeAccountDomainService accountDomainService;
    private final OrganizationMemberDomainService memberDomainService;
    private final CareRelationDomainService careRelationDomainService;
    private final AccessGroupDomainService accessGroupDomainService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final BackofficeAccountRepository accountRepository;
    private final GuardianRecipientLinkRepository linkRepository;
    private final OrganizationRecipients organizationRecipients;
    private final UserAdaptor userAdaptor;

    /** 계정을 만들어 기관에 바로 소속시키고, 대상자를 골랐으면 연결까지 한 번에 한다. 중간에 실패하면 모두 되돌린다. */
    @Transactional
    public GuardianDetailResponse register(BackofficeActor actor, RegisterGuardianRequest request) {
        OrganizationMember registeredBy = actorOf(actor);
        // 대상자 확인을 먼저 해 잘못된 대상자면 계정을 만들지 않는다.
        CareRecipient recipient = StringUtils.hasText(request.careRecipientId())
                ? organizationRecipients.get(actor, request.careRecipientId()) : null;
        BackofficeAccount account = accountDomainService.register(
                request.loginId(), request.password(), request.name(), request.phone());
        OrganizationMember guardian = memberDomainService.invite(
                organizationRepository.getReferenceById(actor.organizationId()), account,
                RoleTemplateCode.GUARDIAN, registeredBy);
        guardian = memberDomainService.approve(guardian, registeredBy);
        if (recipient != null) {
            // 연결은 활성 보호자만 되므로 비활성화보다 먼저 한다.
            link(recipient, guardian, registeredBy, request.relation(), request.relationText());
        }
        if (!request.active()) {
            accountDomainService.suspend(account);
        }
        return detail(guardian);
    }

    @Transactional(readOnly = true)
    public GuardianListResponse list(BackofficeActor actor, GuardianStatusFilter status, String keyword,
                                     int page, int size) {
        String name = StringUtils.hasText(keyword) ? keyword.trim() : null;
        var guardians = memberRepository.findGuardians(actor.organizationId(), name, status.linked(),
                        PageRequest.of(page - 1, size))
                .map(g -> new GuardianListResponse.Item(g.accountUuid(), g.name(),
                        g.accountStatus() == BackofficeAccountStatus.ACTIVE, g.recipientPublicId() != null,
                        g.relation(), GuardianRelation.labelOf(g.relation(), g.relationText()),
                        g.recipientPublicId() == null ? null
                                : new GuardianListResponse.LinkedRecipient(g.recipientPublicId(), g.recipientName())));
        GuardianCounts counts = memberRepository.countGuardians(actor.organizationId(), name);
        return new GuardianListResponse(
                new GuardianListResponse.Counts(counts.total(), counts.linked(), counts.total() - counts.linked()),
                PagedResponse.from(guardians));
    }

    @Transactional(readOnly = true)
    public GuardianDetailResponse get(BackofficeActor actor, String guardianId) {
        return detail(guardianOf(actor, guardianId));
    }

    @Transactional
    public GuardianDetailResponse update(BackofficeActor actor, String guardianId, UpdateGuardianRequest request) {
        OrganizationMember guardian = guardianOf(actor, guardianId);
        BackofficeAccount account = accountDomainService.changeProfile(guardian.getAccount(), request.name(),
                request.phone());
        // 상태가 바뀔 때만 전환한다(정지는 발급된 토큰도 끊으므로 이미 정지된 계정에 다시 걸지 않는다).
        if (request.active() && !account.isActive()) {
            accountDomainService.activate(account);
        } else if (!request.active() && account.isActive()) {
            accountDomainService.suspend(account);
        }
        return detail(guardian);
    }

    /** 연결. 이미 이 대상자와 연결돼 있으면 관계만 바꾼다. 다른 대상자와 연결돼 있으면 4459. */
    @Transactional
    public GuardianDetailResponse link(BackofficeActor actor, String careRecipientId, LinkGuardianRequest request) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        OrganizationMember guardian = guardianOf(actor, request.guardianId());
        link(recipient, guardian, actorOf(actor), request.relation(), request.relationText());
        return detail(guardian);
    }

    /** 연결 해제. 연결 이력은 남는다. 연결돼 있지 않으면 아무것도 하지 않는다. */
    @Transactional
    public GuardianDetailResponse unlink(BackofficeActor actor, String careRecipientId, String guardianId) {
        CareRecipient recipient = organizationRecipients.get(actor, careRecipientId);
        OrganizationMember guardian = guardianOf(actor, guardianId);
        careRelationDomainService.unlinkGuardian(recipient, guardian, actorOf(actor));
        return detail(guardian);
    }

    private void link(CareRecipient recipient, OrganizationMember guardian, OrganizationMember linkedBy,
                      GuardianRelation relation, String relationText) {
        careRelationDomainService.linkGuardian(recipient, guardian, linkedBy)
                .describeRelation(relation, relationText);
    }

    private GuardianDetailResponse detail(OrganizationMember guardian) {
        BackofficeAccount account = guardian.getAccount();
        GuardianDetailResponse.LinkedRecipient linked = linkRepository.findFirstByGuardianAndEndedAtIsNull(guardian)
                .map(this::linkedRecipient)
                .orElse(null);
        return new GuardianDetailResponse(account.getAccountUuid(), account.getLoginId(), account.getName(),
                account.getPhone(), account.isActive(), account.getCreatedDate(), linked);
    }

    /** 이름·생년월일은 어르신 계정(users)에서 읽는다(대상자는 user_id로만 참조). */
    private GuardianDetailResponse.LinkedRecipient linkedRecipient(GuardianRecipientLink link) {
        CareRecipient recipient = link.getRecipient();
        User user = recipient.getUserId() == null ? null : userAdaptor.queryUserById(recipient.getUserId());
        return new GuardianDetailResponse.LinkedRecipient(recipient.getPublicId(),
                user == null ? null : user.getName(), user == null ? null : user.getBirthDate(),
                link.getRelation(), link.getRelationText(), link.relationLabel(), link.getStartedAt());
    }

    private OrganizationMember guardianOf(BackofficeActor actor, String guardianId) {
        return accountRepository.findByAccountUuid(guardianId)
                .flatMap(memberRepository::findCurrentByAccount)
                .filter(member -> member.getOrganization().getId().equals(actor.organizationId()))
                .filter(member -> accessGroupDomainService.primaryTemplateOf(member)
                        .filter(RoleTemplateCode.GUARDIAN::equals).isPresent())
                .orElseThrow(() -> OrganizationHandler.MEMBER_NOT_FOUND);
    }

    private OrganizationMember actorOf(BackofficeActor actor) {
        return memberRepository.getReferenceById(actor.organizationMemberId());
    }
}
