package com.safori.api.operator.service;

import com.safori.api.common.dto.PagedResponse;
import com.safori.api.guardian.dto.GuardianDetailResponse;
import com.safori.api.guardian.dto.RegisterGuardianRequest;
import com.safori.api.guardian.service.GuardianUseCase;
import com.safori.api.operator.dto.OrganizationDetailResponse;
import com.safori.api.operator.dto.OrganizationSummaryResponse;
import com.safori.api.operator.dto.ReplaceOrganizationAdminRequest;
import com.safori.api.operator.dto.ReplaceOrganizationAdminResponse;
import com.safori.api.recipient.dto.CareRecordResponse;
import com.safori.api.recipient.dto.RecipientListResponse;
import com.safori.api.recipient.dto.RegisterRecipientResponse;
import com.safori.api.recipient.service.ListRecipientsUseCase;
import com.safori.api.recipient.service.OrganizationRecipients;
import com.safori.api.user.dto.UserRegisterRequest;
import com.safori.api.user.service.SignUpUseCase;
import com.safori.api.worker.dto.RegisterWorkerRequest;
import com.safori.api.worker.dto.RegisterWorkerResponse;
import com.safori.api.worker.service.RegisterWorkerUseCase;
import com.safori.common.annotation.UseCase;
import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.care.entity.CareProcessingStatus;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.entity.CareRecord;
import com.safori.domain.care.entity.CareStatusCode;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.service.CareRecordDomainService;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationStatus;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.model.OrganizationCount;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.repository.OrganizationRepository;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SAFORI 운영자 콘솔의 기관 목록·상세·이름 변경·상태 변경·관리자 교체. 기관 생성은 {@link CreateOrganizationUseCase}.
 * 관리자·인원 수는 기관 페이지 단위로 한 번에 읽는다(기관마다 조회하지 않는다).
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class OperatorOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CareRecipientRepository recipientRepository;
    private final OrganizationDomainService organizationDomainService;
    private final OrganizationMemberDomainService memberDomainService;
    private final BackofficeAccountDomainService accountDomainService;
    private final RegisterWorkerUseCase registerWorkerUseCase;
    private final GuardianUseCase guardianUseCase;
    private final SignUpUseCase signUpUseCase;
    private final CareRelationDomainService careRelationDomainService;
    private final ListRecipientsUseCase listRecipientsUseCase;
    private final OrganizationRecipients organizationRecipients;
    private final CareRecordDomainService recordDomainService;

    @Transactional(readOnly = true)
    public PagedResponse<OrganizationSummaryResponse> list(String keyword, OrganizationStatus status, int page, int size) {
        Page<Organization> organizations = organizationRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : null, status, PageRequest.of(page - 1, size));
        List<Long> ids = organizations.map(Organization::getId).toList();
        // 빈 IN 절을 만들지 않도록 기관이 없으면 조회하지 않는다.
        Map<Long, OrganizationMember> admins = ids.isEmpty() ? Map.of() : admins(ids);
        Map<Long, Long> workers = ids.isEmpty() ? Map.of()
                : byOrganization(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.CARE_WORKER.name()));
        Map<Long, Long> recipients = ids.isEmpty() ? Map.of()
                : byOrganization(recipientRepository.countActiveByOrganizations(ids));
        return PagedResponse.from(organizations.map(o -> {
            OrganizationMember admin = admins.get(o.getId());
            return new OrganizationSummaryResponse(o.getPublicId(), o.getName(), o.getStatus(),
                    admin == null ? null : new OrganizationSummaryResponse.Admin(
                            admin.getAccount().getLoginId(), admin.getAccount().getName()),
                    workers.getOrDefault(o.getId(), 0L), recipients.getOrDefault(o.getId(), 0L), o.getCreatedDate());
        }));
    }

    @Transactional(readOnly = true)
    public OrganizationDetailResponse get(String organizationPublicId) {
        return detail(organizationOf(organizationPublicId));
    }

    @Transactional
    public void rename(String organizationPublicId, String name) {
        organizationOf(organizationPublicId).rename(name.trim());
    }

    /** 비활성화하면 관리자를 포함한 소속 구성원 전원이 다음 요청부터 권한을 잃는다. 다시 활성화하면 돌아온다. */
    @Transactional
    public void changeStatus(String organizationPublicId, OrganizationStatus status) {
        Organization organization = organizationOf(organizationPublicId);
        if (status == OrganizationStatus.ACTIVE) {
            organizationDomainService.activate(organization);
        } else {
            organizationDomainService.deactivate(organization);
        }
    }

    /**
     * 관리자 교체. 기존 관리자는 소속을 종료하고 계정을 정지해 발급된 토큰도 끊는다(계정은 한 기관에만 소속되므로
     * 다시 쓰지 않는다). 새 관리자 계정을 만들어 기관 생성 때와 같이 바로 ACTIVE 관리자로 둔다.
     * 기관 행을 잠가 동시 교체를 직렬화하고, 새 아이디가 중복이면 기존 관리자도 그대로 남는다(한 트랜잭션).
     */
    @Transactional
    public ReplaceOrganizationAdminResponse replaceAdmin(String organizationPublicId,
                                                         ReplaceOrganizationAdminRequest request) {
        Organization organization = organizationRepository.findByIdForUpdate(organizationOf(organizationPublicId).getId())
                .orElseThrow(() -> OrganizationHandler.NOT_FOUND);
        // 새 계정을 먼저 만들어 아이디 중복이면 기존 관리자를 건드리기 전에 실패한다.
        BackofficeAccount admin = accountDomainService.register(request.adminLoginId(), request.adminPassword(),
                request.adminName(), request.adminPhone());
        for (OrganizationMember current : memberRepository.findCurrentByTemplate(
                List.of(organization.getId()), RoleTemplateCode.ORG_ADMIN.name())) {
            memberDomainService.revoke(current, null);
            accountDomainService.suspend(current.getAccount());
        }
        OrganizationMember member = memberDomainService.invite(organization, admin, RoleTemplateCode.ORG_ADMIN, null);
        memberDomainService.approve(member, null);

        log.info("운영자 기관 관리자 교체: organization={}, adminAccount={}",
                organization.getPublicId(), admin.getAccountUuid());
        return new ReplaceOrganizationAdminResponse(admin.getAccountUuid());
    }

    /** 테스트·지원용: 운영자가 기관 담당자 계정을 만든다. 관리자가 등록한 것과 같고 등록자만 운영(null)이다. */
    @Transactional
    public RegisterWorkerResponse registerManager(String organizationPublicId, RegisterWorkerRequest request) {
        return registerWorkerUseCase.execute(organizationOf(organizationPublicId).getId(), null, request);
    }

    /** 테스트·지원용: 운영자가 기관 보호자 계정을 만든다(대상자를 고르면 연결까지). */
    @Transactional
    public GuardianDetailResponse registerGuardian(String organizationPublicId, RegisterGuardianRequest request) {
        return guardianUseCase.register(organizationOf(organizationPublicId).getId(), null, request);
    }

    /**
     * 테스트·지원용: 어르신 앱 계정을 만들고 바로 기관 대상자로 등록한다(앱 회원가입 + 대상자 추가).
     * 한 트랜잭션이라 등록이 실패하면 앱 계정도 남지 않는다.
     */
    @Transactional
    public RegisterRecipientResponse registerRecipient(String organizationPublicId, UserRegisterRequest request) {
        Organization organization = organizationOf(organizationPublicId);
        Long userId = signUpUseCase.execute(request);
        return new RegisterRecipientResponse(careRelationDomainService.registerRecipient(organization, userId).getPublicId());
    }

    /** 테스트·지원용: 기관의 대상자 목록(현재 상태 코드 포함). 상태를 바꿀 대상자를 고르는 데 쓴다. */
    @Transactional(readOnly = true)
    public RecipientListResponse recipients(String organizationPublicId, CareStatusCode statusCode, String keyword,
                                            int page, int size) {
        return listRecipientsUseCase.forOperator(organizationOf(organizationPublicId).getId(), statusCode, keyword,
                page, size);
    }

    /**
     * 테스트·지원용: 대상자의 현재 상태 코드를 판정 조건 없이 바로 바꾼다.
     * 덮어쓰기 규칙(낮은 등급은 흡수)을 그대로 타면 등급을 내릴 수 없으므로, 현재 사유를 운영(시스템) 완료로 닫고
     * 요청한 등급의 대표 사유를 새로 올린다. {@code statusCode}가 null이면 닫기만 해 표시 없음(X)이 된다.
     *
     * @return 새 현재 기록. 표시 없음으로 바꿨으면 null
     */
    @Transactional
    public CareRecordResponse changeRecipientStatus(String organizationPublicId, String careRecipientId,
                                                    CareStatusCode statusCode, String reasonMessage) {
        CareRecipient recipient = organizationRecipients.get(organizationOf(organizationPublicId).getId(), careRecipientId);
        CareRecord current = recipient.getCurrentRecord();
        if (current != null) {
            recordDomainService.process(recipient, current.getPublicId(), CareProcessingStatus.DONE, null);
        }
        if (statusCode == null) {
            return null;
        }
        CareReasonType reasonType = TEST_REASONS.get(statusCode);
        String message = StringUtils.hasText(reasonMessage) ? reasonMessage.trim() : TEST_MESSAGES.get(statusCode);
        return CareRecordResponse.of(recordDomainService.raise(recipient, reasonType, message, LocalDateTime.now()));
    }

    /** 상태 코드별 대표 사유와 기본 문구(판정이 실제로 만드는 문구와 같은 형태). */
    private static final Map<CareStatusCode, CareReasonType> TEST_REASONS = Map.of(
            CareStatusCode.URGENT, CareReasonType.HELP_REQUEST,
            CareStatusCode.CAUTION, CareReasonType.SAME_EMOTION_REPEAT,
            CareStatusCode.INTEREST, CareReasonType.COUNSEL_EXTENSION_REPEAT);
    private static final Map<CareStatusCode, String> TEST_MESSAGES = Map.of(
            CareStatusCode.URGENT, "119에 SOS 요청을 했어요.",
            CareStatusCode.CAUTION, "최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.",
            CareStatusCode.INTEREST, "최근 상담 3회 중 2회에서 '조금 더 이야기하기'를 선택했어요.");

    private OrganizationDetailResponse detail(Organization organization) {
        List<Long> ids = List.of(organization.getId());
        OrganizationMember admin = admins(ids).get(organization.getId());
        BackofficeAccount account = admin == null ? null : admin.getAccount();
        return new OrganizationDetailResponse(organization.getPublicId(), organization.getName(),
                organization.getStatus(),
                account == null ? null : new OrganizationDetailResponse.Admin(account.getAccountUuid(),
                        account.getLoginId(), account.getName(), account.getPhone(), account.getStatus()),
                count(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.CARE_WORKER.name())),
                count(memberRepository.countCurrentByTemplate(ids, RoleTemplateCode.GUARDIAN.name())),
                count(recipientRepository.countActiveByOrganizations(ids)),
                organization.getCreatedDate(), organization.getLastModifiedDate());
    }

    private Organization organizationOf(String organizationPublicId) {
        return organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(() -> OrganizationHandler.NOT_FOUND);
    }

    private Map<Long, OrganizationMember> admins(Collection<Long> organizationIds) {
        return memberRepository.findCurrentByTemplate(organizationIds, RoleTemplateCode.ORG_ADMIN.name()).stream()
                .collect(Collectors.toMap(m -> m.getOrganization().getId(), Function.identity(), (a, b) -> a));
    }

    private static Map<Long, Long> byOrganization(List<OrganizationCount> counts) {
        return counts.stream().collect(Collectors.toMap(OrganizationCount::organizationId, OrganizationCount::count));
    }

    private static long count(List<OrganizationCount> counts) {
        return counts.isEmpty() ? 0 : counts.get(0).count();
    }
}
