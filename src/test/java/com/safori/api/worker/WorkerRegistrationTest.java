package com.safori.api.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import com.safori.domain.account.repository.BackofficeAccountRepository;
import com.safori.domain.organization.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 담당자 등록: 관리자 기관에 바로 ACTIVE 담당자로 소속, 등록한 계정으로 CARE_WORKER 로그인.
 */
@SpringBootTest
@Transactional
class WorkerRegistrationTest {

    private static final String REGISTER = "/v1/api/backoffice/workers";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired BackofficeAccountRepository accountRepository;
    @Autowired OrganizationMemberRepository memberRepository;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("사포리 복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        adminToken = "Bearer " + signIn("orgadmin01", "tempPass1234")
                .andReturn().getResponse().getContentAsString().transform(this::accessToken);
    }

    @Test
    @DisplayName("등록한 담당자는 바로 ACTIVE로 소속되고, 그 계정으로 로그인하면 role=CARE_WORKER")
    void registeredWorkerCanSignIn() throws Exception {
        register(body("worker01", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workerId").isNotEmpty())
                .andExpect(jsonPath("$.result.loginId").value("worker01"));

        var account = accountRepository.findByLoginId("worker01").orElseThrow();
        assertThat(account.getPhone()).isEqualTo("01012345678");
        assertThat(memberRepository.findCurrentByAccount(account).orElseThrow().getJobTitle()).isEqualTo("사회복지사");

        signIn("worker01", "workPass1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("CARE_WORKER"));
    }

    @Test
    @DisplayName("비활성화로 등록하면 로그인 불가(4351), 아이디 중복은 4350, 비밀번호 규칙 위반은 400")
    void inactiveDuplicateAndInvalidInput() throws Exception {
        register(body("worker02", false)).andExpect(status().isOk());
        signIn("worker02", "workPass1234").andExpect(jsonPath("$.code").value(4351));

        register(body("worker02", true)).andExpect(jsonPath("$.code").value(4350));

        register(body("worker03", true).replace("workPass1234", "onlyletters"))
                .andExpect(status().isBadRequest());
    }

    private static String body(String loginId, boolean active) {
        return """
                {"name":"박담당","phone":"010-1234-5678","jobTitle":"사회복지사","active":%s,
                 "loginId":"%s","password":"workPass1234"}
                """.formatted(active, loginId);
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post(REGISTER).header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions signIn(String username, String password) throws Exception {
        return mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String accessToken(String responseBody) {
        try {
            return objectMapper.readTree(responseBody).at("/result/accessToken").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
