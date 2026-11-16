package com.safori.security;

import com.safori.domain.access.service.AccessRoleDomainService;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.service.CareRelationDomainService;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.service.OrganizationDomainService;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import com.safori.security.BackofficeFixture.Scenario;
import com.safori.security.dto.AccountRole;
import com.safori.security.service.BackofficeTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;

import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static com.safori.security.BackofficeSecurityTestConfig.ASSIGNMENTS;
import static com.safori.security.BackofficeSecurityTestConfig.GUARDIAN_STATUS;
import static com.safori.security.BackofficeSecurityTestConfig.RECIPIENT;
import static com.safori.security.BackofficeSecurityTestConfig.UNMAPPED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 필터 단 인가: 백오피스 토큰 인증 → 요청 시점 DB 상태로 주체·권한 재계산 → URL 규칙(권한·어르신 범위) → 기본 거부.
 * 테스트 전용 엔드포인트와 규칙은 {@link BackofficeSecurityTestConfig}에 있다.
 */
@SpringBootTest
@Transactional
@Import(BackofficeSecurityTestConfig.class)
class BackofficeSecurityFilterChainTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired BackofficeTokenService tokenService;
    @Autowired BackofficeFixture fixture;
    @Autowired OrganizationDomainService organizationService;
    @Autowired BackofficeAccountDomainService accountService;
    @Autowired AccessRoleDomainService roleService;
    @Autowired CareRelationDomainService careRelationService;
    @Autowired OrganizationMemberDomainService memberService;

    @Value("${token.secret-user}")
    String userSecret;

    private MockMvc mockMvc;
    private Scenario s;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        s = fixture.scenario();
    }

    @Test
    @DisplayName("토큰이 없거나 검증에 실패하면 401")
    void missingOrInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("담당자별 배정 대상 조회: 관리자만 통과하고 담당자·보호자는 403")
    void assignmentReadIsAdminOnly() throws Exception {
        requestAs(s.admin(), ASSIGNMENTS).andExpect(status().isOk());
        requestAs(s.worker(), ASSIGNMENTS)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
        requestAs(s.guardian(), ASSIGNMENTS).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("어르신 조회: 관리자는 소속 기관 전체, 담당자는 배정된, 보호자는 연결된 어르신만 통과")
    void recipientReadIsScopedAtFilter() throws Exception {
        requestAs(s.admin(), RECIPIENT, s.recipient()).andExpect(status().isOk());
        requestAs(s.admin(), RECIPIENT, s.otherRecipient()).andExpect(status().isOk());
        requestAs(s.admin(), RECIPIENT, s.foreignRecipient()).andExpect(status().isForbidden());

        requestAs(s.worker(), RECIPIENT, s.recipient())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(s.recipient().getPublicId()));
        requestAs(s.worker(), RECIPIENT, s.otherRecipient()).andExpect(status().isForbidden());

        requestAs(s.guardian(), RECIPIENT, s.recipient()).andExpect(status().isOk());
        requestAs(s.guardian(), RECIPIENT, s.otherRecipient()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("보호자 경로: 연결된 보호자만 공개 상태를 조회하고, 담당자·관리자는 403")
    void guardianPathIsGuardianOnly() throws Exception {
        requestAs(s.guardian(), GUARDIAN_STATUS, s.recipient()).andExpect(status().isOk());
        requestAs(s.guardian(), GUARDIAN_STATUS, s.otherRecipient()).andExpect(status().isForbidden());
        requestAs(s.worker(), GUARDIAN_STATUS, s.recipient()).andExpect(status().isForbidden());
        requestAs(s.admin(), GUARDIAN_STATUS, s.recipient()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("규칙이 없는 백오피스 경로는 관리자라도 403, 토큰이 없으면 401")
    void unmappedPathIsDeniedByDefault() throws Exception {
        requestAs(s.admin(), UNMAPPED).andExpect(status().isForbidden());
        mockMvc.perform(get(UNMAPPED)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 토큰이라도 배정 종료·권한 회수는 다음 요청부터 반영된다")
    void authorizationFollowsCurrentState() throws Exception {
        String workerToken = bearer(s.worker());
        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isOk());

        careRelationService.endAssignment(s.recipient(), s.admin());
        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden());

        String adminToken = bearer(s.admin());
        roleService.revokePermission(roleService.getTemplateRole(s.organization(), ORG_ADMIN), RECIPIENT_READ);
        mockMvc.perform(get(RECIPIENT, s.otherRecipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("계정 정지·기관 비활성화 후에는 기존 토큰이 401")
    void suspendedAccountOrInactiveOrganizationIsUnauthorized() throws Exception {
        String workerToken = bearer(s.worker());
        accountService.suspend(s.worker().getAccount());
        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isUnauthorized());

        String adminToken = bearer(s.admin());
        organizationService.deactivate(s.organization());
        mockMvc.perform(get(ASSIGNMENTS).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("강제 로그아웃(auth_version 증가)하면 계정이 활성이어도 이전 토큰은 401, 새 토큰은 통과")
    void revokedTokensAreUnauthorized() throws Exception {
        String oldToken = bearer(s.worker());
        accountService.revokeIssuedTokens(s.worker().getAccount());

        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, oldToken))
                .andExpect(status().isUnauthorized());
        requestAs(s.worker(), RECIPIENT, s.recipient()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 기관을 컨텍스트로 발급된 토큰은 그 기관 소속이 아니면 401")
    void tokenForForeignOrganizationIsUnauthorized() throws Exception {
        OrganizationMember worker = s.worker();
        String token = "Bearer " + tokenService.issueAccessToken(
                worker.getAccount().getAccountUuid(),
                s.foreignOrganization().getPublicId(),
                worker.getAccount().getAuthVersion(), AccountRole.CARE_WORKER);

        mockMvc.perform(get(RECIPIENT, s.foreignRecipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("어르신 앱 토큰으로는 백오피스 경로에, 백오피스 토큰으로는 어르신 앱 API에 들어갈 수 없다")
    void tokensDoNotCrossChains() throws Exception {
        String appUserToken = "Bearer " + Jwts.builder()
                .setSubject("user01")
                .claim("auth", "ROLE_USER")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(userSecret)), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get(RECIPIENT, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, appUserToken))
                .andExpect(status().isUnauthorized());
        // 내 정보 조회(GET /v1/api/users)만 두 토큰을 모두 받는다. 그 밖의 어르신 앱 API는 막힌다.
        mockMvc.perform(get("/v1/api/users/voices/recent").header(HttpHeaders.AUTHORIZATION, bearer(s.admin())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("새로 초대된 담당자는 승인 전까지 401, 승인·배정 후에는 배정된 어르신만 통과")
    void invitedWorkerNeedsApprovalAndAssignment() throws Exception {
        OrganizationMember newWorker = fixture.pendingMember(s.organization(), CARE_WORKER);
        requestAs(newWorker, RECIPIENT, s.otherRecipient()).andExpect(status().isUnauthorized());

        memberService.approve(newWorker, s.admin());
        careRelationService.assignWorker(s.otherRecipient(), newWorker, s.admin(), "신규 담당자 배정");

        requestAs(newWorker, RECIPIENT, s.otherRecipient()).andExpect(status().isOk());
        requestAs(newWorker, RECIPIENT, s.recipient()).andExpect(status().isForbidden());
    }

    private ResultActions requestAs(OrganizationMember member, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private ResultActions requestAs(OrganizationMember member, String path, CareRecipient recipient) throws Exception {
        return mockMvc.perform(get(path, recipient.getPublicId()).header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private String bearer(OrganizationMember member) {
        return "Bearer " + tokenService.issueAccessToken(
                member.getAccount().getAccountUuid(),
                member.getOrganization().getPublicId(),
                member.getAccount().getAuthVersion(), AccountRole.CARE_WORKER);
    }
}
