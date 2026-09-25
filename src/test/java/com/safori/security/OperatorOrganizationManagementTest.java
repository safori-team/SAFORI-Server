package com.safori.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 운영자 기관 목록·상세·이름 변경·상태 변경·관리자 교체.
 */
@SpringBootTest
@Transactional
class OperatorOrganizationManagementTest {

    private static final String URL = "/v1/api/operator/organizations";
    private static final String KEY = "test-only-operator-key";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String happy;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        happy = create("행복복지관", "orgadmin01");
        create("사랑복지관", "orgadmin02");
    }

    @Test
    @DisplayName("목록: 최근 생성순, 기관명 검색·상태 필터, 관리자와 담당자·대상자 수")
    void list() throws Exception {
        String adminToken = signIn("orgadmin01", "tempPass1234");
        mockMvc.perform(post("/v1/api/admin/managers").header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"김철수","phone":"010-2222-3333","jobTitle":"사회복지사","active":true,
                         "loginId":"worker01","password":"workPass1234"}
                        """));

        operator(get(URL), null)
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.items[0].name").value("사랑복지관"))
                .andExpect(jsonPath("$.result.items[1].admin.loginId").value("orgadmin01"))
                .andExpect(jsonPath("$.result.items[1].careWorkerCount").value(1))
                .andExpect(jsonPath("$.result.items[1].recipientCount").value(0));
        operator(get(URL).param("keyword", "행복"), null)
                .andExpect(jsonPath("$.result.totalElements").value(1));
        operator(get(URL).param("status", "INACTIVE"), null)
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상세·이름 변경·비활성화(관리자 토큰 끊김)·없는 기관은 404/4308")
    void detailRenameAndStatus() throws Exception {
        String adminToken = signIn("orgadmin01", "tempPass1234");
        operator(get(URL + "/" + happy), null)
                .andExpect(jsonPath("$.result.admin.phone").value("01011112222"))
                .andExpect(jsonPath("$.result.admin.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.guardianCount").value(0));

        operator(patch(URL + "/" + happy), "{\"name\":\"행복복지센터\"}")
                .andExpect(jsonPath("$.isSuccess").value(true));
        operator(patch(URL + "/" + happy + "/status"), "{\"status\":\"INACTIVE\"}");
        operator(get(URL + "/" + happy), null)
                .andExpect(jsonPath("$.result.name").value("행복복지센터"))
                .andExpect(jsonPath("$.result.status").value("INACTIVE"));
        mockMvc.perform(get("/v1/api/admin/managers").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.isSuccess").value(false));

        operator(get(URL + "/no-such-org"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4308));
    }

    @Test
    @DisplayName("관리자 교체: 기존 관리자는 끊기고 새 관리자로 로그인된다. 아이디 중복이면 그대로")
    void replaceAdmin() throws Exception {
        String oldToken = signIn("orgadmin01", "tempPass1234");
        String body = """
                {"adminName":"새관리","adminPhone":"010-5555-6666","adminLoginId":"%s","adminPassword":"newPass1234"}
                """;

        operator(put(URL + "/" + happy + "/admin"), body.formatted("orgadmin02"))
                .andExpect(jsonPath("$.code").value(4350));
        operator(get(URL + "/" + happy), null).andExpect(jsonPath("$.result.admin.loginId").value("orgadmin01"));

        operator(put(URL + "/" + happy + "/admin"), body.formatted("orgadmin09"))
                .andExpect(jsonPath("$.result.adminAccountUuid").exists());
        operator(get(URL + "/" + happy), null)
                .andExpect(jsonPath("$.result.admin.loginId").value("orgadmin09"))
                .andExpect(jsonPath("$.result.admin.name").value("새관리"));
        mockMvc.perform(get("/v1/api/admin/managers").header(HttpHeaders.AUTHORIZATION, oldToken))
                .andExpect(jsonPath("$.isSuccess").value(false));
        mockMvc.perform(get("/v1/api/admin/managers").header(HttpHeaders.AUTHORIZATION, signIn("orgadmin09", "newPass1234")))
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    private String create(String name, String adminLoginId) throws Exception {
        return json(operator(post(URL), """
                {"organizationName":"%s","adminLoginId":"%s","adminPassword":"tempPass1234","adminName":"이관리","adminPhone":"010-1111-2222"}
                """.formatted(name, adminLoginId))).at("/result/organizationPublicId").asText();
    }

    private String signIn(String loginId, String password) throws Exception {
        return "Bearer " + json(mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}")))
                .at("/result/accessToken").asText();
    }

    private ResultActions operator(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("X-Operator-Key", KEY);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
