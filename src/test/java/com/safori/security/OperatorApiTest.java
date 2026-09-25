package com.safori.security;

import com.safori.domain.access.entity.RoleTemplateCode;
import com.safori.domain.account.entity.BackofficeAccount;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.organization.entity.Organization;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.domain.organization.entity.OrganizationMemberStatus;
import com.safori.domain.organization.exception.OrganizationHandler;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import com.safori.domain.organization.service.OrganizationMemberDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 운영자 API: X-Operator-Key 인증 → 기관 + ACTIVE 기관 관리자 생성. 관리자는 기관당 1명.
 */
@SpringBootTest
@Transactional
class OperatorApiTest {

    private static final String URL = "/v1/api/operator/organizations";
    private static final String BODY = """
            {"organizationName":"사포리 복지관","adminLoginId":"operator_admin","adminPassword":"tempPass1234","adminName":"관리자"}
            """;

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired BackofficeAccountRepository accountRepository;
    @Autowired OrganizationMemberRepository memberRepository;
    @Autowired BackofficeAccountDomainService accountService;
    @Autowired OrganizationMemberDomainService memberService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    @DisplayName("운영자 키가 없거나 틀리면 401")
    void missingOrWrongKeyIsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header("X-Operator-Key", "wrong").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        assertThat(accountRepository.existsByLoginId("operator_admin")).isFalse();
    }

    @Test
    @DisplayName("운영자 키가 맞으면 기관과 ACTIVE 기관 관리자를 만들고, 그 기관에 관리자를 더 초대할 수 없다")
    void createsOrganizationWithSingleActiveAdmin() throws Exception {
        mockMvc.perform(post(URL).header("X-Operator-Key", "test-only-operator-key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.organizationPublicId").exists());

        BackofficeAccount admin = accountRepository.findAll().stream()
                .filter(a -> a.getLoginId().equals("operator_admin")).findFirst().orElseThrow();
        OrganizationMember member = memberRepository.findAll().stream()
                .filter(m -> m.getAccount().getId().equals(admin.getId())).findFirst().orElseThrow();
        assertThat(member.getStatus()).isEqualTo(OrganizationMemberStatus.ACTIVE);

        Organization organization = member.getOrganization();
        BackofficeAccount another = accountService.register("second_admin", "tempPass1234", "두번째");
        assertThatThrownBy(() -> memberService.invite(organization, another, RoleTemplateCode.ORG_ADMIN, member))
                .isEqualTo(OrganizationHandler.ADMIN_ALREADY_EXISTS);
    }
}
