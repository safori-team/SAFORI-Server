package com.safori.security;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 백오피스 API 권한 규칙({@code AdminApiAuthorizationRules}): 역할별로 열리는 경로와 대상자 범위, 응답에서 가리는 필드.
 */
@SpringBootTest
@Transactional
class AdminApiAuthorizationTest {

    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String admin;
    private String worker;
    private String guardian;
    private String assigned;
    private String other;
    private String hiddenJournal;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        admin = signIn("orgadmin01", "tempPass1234");

        assigned = recipient("elder001", "김영희");
        other = recipient("elder002", "홍길동");
        String managerId = json(call(admin, post("/v1/api/admin/managers"), """
                {"name":"박지현","phone":"01012345678","jobTitle":"사회복지사","active":true,
                 "loginId":"worker001","password":"workPass1234"}
                """)).at("/result/managerId").asText();
        call(admin, post(RECIPIENTS + "/" + assigned + "/manager"), "{\"managerId\":\"" + managerId + "\"}");
        call(admin, post("/v1/api/admin/guardians"), """
                {"name":"김희영","phone":"010-3333-4444","careRecipientId":"%s","relation":"CHILD","active":true,
                 "loginId":"guard001","password":"guardPass1234"}
                """.formatted(assigned));
        worker = signIn("worker001", "workPass1234");
        guardian = signIn("guard001", "guardPass1234");

        call(worker, post(RECIPIENTS + "/" + assigned + "/journals"), journal(true));
        hiddenJournal = json(call(worker, post(RECIPIENTS + "/" + assigned + "/journals"), journal(false)))
                .at("/result/journalId").asText();
    }

    @Test
    @DisplayName("관리자 전용: 대상자 추가·정보 수정, 담당자·보호자 명부, 배정, 보호자 연결은 담당자·보호자 403")
    void adminOnly() throws Exception {
        for (String token : new String[]{worker, guardian}) {
            call(token, post(RECIPIENTS), "{\"loginId\":\"elder002\"}").andExpect(status().isForbidden());
            call(token, put(RECIPIENTS + "/" + assigned), "{\"name\":\"김영희\",\"active\":true,\"loginId\":\"elder001\"}")
                    .andExpect(status().isForbidden());
            call(token, get("/v1/api/admin/managers"), null).andExpect(status().isForbidden());
            call(token, get("/v1/api/admin/guardians"), null).andExpect(status().isForbidden());
            call(token, put(RECIPIENTS + "/" + assigned + "/guardians"), "{\"guardianId\":\"x\",\"relation\":\"CHILD\"}")
                    .andExpect(status().isForbidden());
        }
        call(admin, get("/v1/api/admin/guardians"), null).andExpect(jsonPath("$.result.counts.total").value(1));
    }

    @Test
    @DisplayName("담당자: 배정 대상자만 상세·기록·일지가 열리고 연결 보호자·전체 일지가 보인다")
    void workerScope() throws Exception {
        call(worker, get(RECIPIENTS + "/" + assigned), null)
                .andExpect(jsonPath("$.result.guardians[0].name").value("김희영"))
                .andExpect(jsonPath("$.result.recentActions.length()").value(2));
        call(worker, get(RECIPIENTS + "/" + other), null).andExpect(status().isForbidden());
        call(worker, post(RECIPIENTS + "/" + other + "/journals"), journal(true)).andExpect(status().isForbidden());
        call(worker, get(RECIPIENTS + "/" + assigned + "/journals/" + hiddenJournal), null)
                .andExpect(jsonPath("$.result.guardianVisible").value(false));
    }

    @Test
    @DisplayName("보호자: 연결 대상자 상세는 보호자 정보 없이 공개 일지만, 기록·일지 작성·처리 상태는 403, 일지 목록은 공개 일지만")
    void guardianScope() throws Exception {
        call(guardian, get(RECIPIENTS + "/" + assigned), null)
                .andExpect(jsonPath("$.result.guardians").isEmpty())
                .andExpect(jsonPath("$.result.recentActions.length()").value(1));
        call(guardian, get(RECIPIENTS + "/" + other), null).andExpect(status().isForbidden());
        call(guardian, post(RECIPIENTS + "/" + assigned + "/journals"), journal(true)).andExpect(status().isForbidden());
        call(guardian, patch(RECIPIENTS + "/" + assigned + "/journals/" + hiddenJournal), "{\"guardianVisible\":true}")
                .andExpect(status().isForbidden());
        call(guardian, get(RECIPIENTS + "/" + assigned + "/records/no-such-record"), null)
                .andExpect(status().isForbidden());
        call(guardian, get("/v1/api/admin/journals").param("from", "2026-09-01").param("to", "2026-09-30"), null)
                .andExpect(jsonPath("$.result.journals.totalElements").value(1));
        call(guardian, get(RECIPIENTS + "/" + assigned + "/journals/" + hiddenJournal), null)
                .andExpect(jsonPath("$.code").value(4458));
    }

    private static String journal(boolean guardianVisible) {
        return """
                {"confirmedAt":"2026-09-13T14:00:00","guardianVisible":%s,
                 "selections":[{"optionCode":"VISIT"},{"optionCode":"CONTACTED"},{"optionCode":"NO_ISSUE"}]}
                """.formatted(guardianVisible);
    }

    private String recipient(String loginId, String name) throws Exception {
        userDomainService.registerUser(loginId, "elderPass1", name, Gender.FEMALE, LocalDate.of(1960, 3, 12), null, null);
        return json(call(admin, post(RECIPIENTS), "{\"loginId\":\"" + loginId + "\"}")).at("/result/recipientPublicId").asText();
    }

    private String signIn(String loginId, String password) throws Exception {
        return "Bearer " + json(mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}")))
                .at("/result/accessToken").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
