package com.safori.api.recipient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.service.UserDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 대상자 추가: 아이디 정확 일치 조회(마스킹) → 로그인한 구성원의 기관에 등록. 권한은 아직 붙지 않았고 로그인만 요구한다.
 */
@SpringBootTest
@Transactional
class RecipientRegistrationTest {

    private static final String LOOKUP = "/v1/api/admin/care-recipients/lookup";
    private static final String REGISTER = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("사포리 복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        userDomainService.registerUser("elder01", "elderPass1", "김철수", Gender.MALE,
                LocalDate.of(1960, 3, 12), "010-1234-5678", null);
        String body = mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"orgadmin01\",\"password\":\"tempPass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        adminToken = "Bearer " + objectMapper.readTree(body).at("/result/accessToken").asText();
    }

    @Test
    @DisplayName("아이디가 정확하면 마스킹된 정보로 조회되고, 등록하면 registered=true, 다시 등록하면 4450")
    void lookupThenRegister() throws Exception {
        mockMvc.perform(get(LOOKUP).param("loginId", "elder01").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("김*수"))
                .andExpect(jsonPath("$.result.phone").value("010-****-5678"))
                .andExpect(jsonPath("$.result.birthDate").value("1960-**-**"))
                .andExpect(jsonPath("$.result.registered").value(false));

        mockMvc.perform(post(REGISTER).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"elder01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientPublicId").isNotEmpty());

        mockMvc.perform(get(LOOKUP).param("loginId", "elder01").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.result.registered").value(true));
        mockMvc.perform(post(REGISTER).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"elder01\"}"))
                .andExpect(jsonPath("$.code").value(4450));
    }

    @Test
    @DisplayName("부분 일치는 조회되지 않고(4052), 백오피스 토큰이 없으면 401")
    void exactMatchOnlyAndLoginRequired() throws Exception {
        mockMvc.perform(get(LOOKUP).param("loginId", "elder").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.code").value(4052));
        mockMvc.perform(get(LOOKUP).param("loginId", "elder01"))
                .andExpect(status().isUnauthorized());
    }
}
