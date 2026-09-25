package com.safori.api.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.service.CreateOrganizationUseCase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 내 정보: 조회(연락처·직종)와 수정(관리자는 이름·연락처·아이디·비밀번호, 담당자는 비밀번호만).
 */
@SpringBootTest
@Transactional
class MyAccountTest {

    private static final String ME = "/v1/api/admin/me";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        adminToken = bearer(signIn("orgadmin01", "tempPass1234"));
    }

    @Test
    @DisplayName("관리자: 이름·연락처·아이디·비밀번호를 바꾸면 새 값으로 로그인되고 리프레시 토큰은 이어진다")
    void adminUpdatesAll() throws Exception {
        String refreshToken = signIn("orgadmin01", "tempPass1234").at("/result/refreshToken").asText();

        perform(put(ME), adminToken, """
                {"name":"이관리2","phone":"010-7777-8888","loginId":"orgadmin02","password":"newPass1234"}
                """)
                .andExpect(jsonPath("$.result.loginId").value("orgadmin02"))
                .andExpect(jsonPath("$.result.name").value("이관리2"))
                .andExpect(jsonPath("$.result.phone").value("01077778888"));

        mockMvc.perform(post("/v1/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(jsonPath("$.isSuccess").value(true));
        String token = bearer(signIn("orgadmin02", "newPass1234"));
        perform(get("/v1/api/users"), token, null)
                .andExpect(jsonPath("$.result.username").value("orgadmin02"))
                .andExpect(jsonPath("$.result.phone").value("01077778888"));
    }

    @Test
    @DisplayName("담당자: 조회에 직종·연락처가 있고, 비밀번호만 바꿀 수 있다(이름·아이디 변경은 4352)")
    void workerChangesPasswordOnly() throws Exception {
        perform(post("/v1/api/admin/managers"), adminToken, """
                {"name":"김철수","phone":"010-2222-3333","jobTitle":"사회복지사","active":true,
                 "loginId":"worker01","password":"workPass1234"}
                """);
        String workerToken = bearer(signIn("worker01", "workPass1234"));

        perform(get("/v1/api/users"), workerToken, null)
                .andExpect(jsonPath("$.result.jobTitle").value("사회복지사"))
                .andExpect(jsonPath("$.result.phone").value("01022223333"));

        perform(put(ME), workerToken, "{\"name\":\"김철수2\"}").andExpect(jsonPath("$.code").value(4352));
        perform(put(ME), workerToken, "{\"loginId\":\"worker02\"}").andExpect(jsonPath("$.code").value(4352));
        perform(put(ME), workerToken, "{\"loginId\":\"worker01\",\"password\":\"newWork1234\"}")
                .andExpect(jsonPath("$.isSuccess").value(true));
        signIn("worker01", "newWork1234");
    }

    private JsonNode signIn(String loginId, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn().getResponse().getContentAsString());
    }

    private static String bearer(JsonNode signIn) {
        return "Bearer " + signIn.at("/result/accessToken").asText();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
