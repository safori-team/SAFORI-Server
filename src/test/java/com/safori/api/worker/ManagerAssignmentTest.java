package com.safori.api.worker;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 담당자 목록·상세와 배정·변경·해제·일괄 변경.
 */
@SpringBootTest
@Transactional
class ManagerAssignmentTest {

    private static final String MANAGERS = "/v1/api/admin/managers";
    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String adminToken;
    private String workerA;
    private String workerB;
    private String inactiveWorker;
    private String r1;
    private String r2;
    private String r3;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        adminToken = "Bearer " + json(perform(post("/v1/api/auth/sign-in"),
                "{\"username\":\"orgadmin01\",\"password\":\"tempPass1234\"}")).at("/result/accessToken").asText();

        workerA = registerWorker("workera01", "김철수", true);
        workerB = registerWorker("workerb01", "박지현", true);
        inactiveWorker = registerWorker("workerc01", "최비활", false);
        r1 = registerRecipient("elder001", "홍길동");
        r2 = registerRecipient("elder002", "김영희");
        r3 = registerRecipient("elder003", "이순자");
    }

    @Test
    @DisplayName("목록: 탭 개수·상태 필터·이름 검색, 배정 인원 수")
    void listManagers() throws Exception {
        perform(put(RECIPIENTS + "/" + r1 + "/manager"), assignBody(workerA)).andExpect(status().isOk());

        perform(get(MANAGERS), null)
                .andExpect(jsonPath("$.result.counts.total").value(3))
                .andExpect(jsonPath("$.result.counts.active").value(2))
                .andExpect(jsonPath("$.result.counts.inactive").value(1))
                .andExpect(jsonPath("$.result.managers.items[0].name").value("김철수"))
                .andExpect(jsonPath("$.result.managers.items[0].assignedCount").value(1));
        perform(get(MANAGERS).param("status", "INACTIVE"), null)
                .andExpect(jsonPath("$.result.managers.items.length()").value(1))
                .andExpect(jsonPath("$.result.managers.items[0].active").value(false));
        perform(get(MANAGERS).param("keyword", "지현"), null)
                .andExpect(jsonPath("$.result.counts.total").value(1))
                .andExpect(jsonPath("$.result.managers.items[0].managerId").value(workerB));
    }

    @Test
    @DisplayName("배정 → 변경 → 해제: 상세의 배정 대상자에 반영되고, 비활성 담당자·없는 대상자는 거부")
    void assignChangeUnassign() throws Exception {
        perform(post(RECIPIENTS + "/" + r1 + "/manager"), assignBody(workerA))
                .andExpect(jsonPath("$.result.managerId").value(workerA));
        perform(get(MANAGERS + "/" + workerA), null)
                .andExpect(jsonPath("$.result.organizationName").value("행복복지관"))
                .andExpect(jsonPath("$.result.role").value("CARE_WORKER"))
                .andExpect(jsonPath("$.result.assignedCount").value(1))
                .andExpect(jsonPath("$.result.assignedRecipients[0].name").value("홍길동"))
                .andExpect(jsonPath("$.result.assignedRecipients[0].birthDate").value("1960-03-12"));

        perform(put(RECIPIENTS + "/" + r1 + "/manager"), assignBody(workerB)).andExpect(status().isOk());
        assertThat(assignedCount(workerA)).isZero();
        assertThat(assignedCount(workerB)).isEqualTo(1);

        perform(delete(RECIPIENTS + "/" + r1 + "/manager"), null)
                .andExpect(jsonPath("$.result.managerId").doesNotExist());
        assertThat(assignedCount(workerB)).isZero();

        perform(put(RECIPIENTS + "/" + r1 + "/manager"), assignBody(inactiveWorker))
                .andExpect(jsonPath("$.code").value(4452));
        perform(put(RECIPIENTS + "/no-such-recipient/manager"), assignBody(workerA))
                .andExpect(jsonPath("$.code").value(4454));
    }

    @Test
    @DisplayName("일괄 변경: 목록과 똑같이 맞춘다 — 빠진 대상자는 해제, 다른 담당자의 대상자는 옮김")
    void replaceRecipients() throws Exception {
        perform(put(RECIPIENTS + "/" + r3 + "/manager"), assignBody(workerB)).andExpect(status().isOk());

        replace(workerA, r1, r2).andExpect(status().isOk());
        assertThat(assignedCount(workerA)).isEqualTo(2);

        replace(workerA, r2, r3).andExpect(jsonPath("$.result.careRecipientIds.length()").value(2));
        assertThat(assignedCount(workerA)).isEqualTo(2);
        assertThat(assignedCount(workerB)).isZero();
        perform(get(MANAGERS).param("status", "ACTIVE"), null)
                .andExpect(jsonPath("$.result.managers.items[?(@.managerId == '%s')].assignedCount".formatted(workerA))
                        .value(2));

        replace(workerA, r1, "no-such-recipient").andExpect(jsonPath("$.code").value(4454));
        assertThat(assignedCount(workerA)).isEqualTo(2);

        replace(workerA).andExpect(status().isOk());
        assertThat(assignedCount(workerA)).isZero();
    }

    private ResultActions replace(String managerId, String... recipientIds) throws Exception {
        String ids = String.join("\",\"", recipientIds);
        String body = recipientIds.length == 0 ? "{\"careRecipientIds\":[]}" : "{\"careRecipientIds\":[\"" + ids + "\"]}";
        return perform(put(MANAGERS + "/" + managerId + "/care-recipients"), body);
    }

    private int assignedCount(String managerId) throws Exception {
        return json(perform(get(MANAGERS + "/" + managerId), null)).at("/result/assignedCount").asInt();
    }

    private String registerWorker(String loginId, String name, boolean active) throws Exception {
        String body = """
                {"name":"%s","phone":"010-1234-5678","jobTitle":"사회복지사","active":%s,
                 "loginId":"%s","password":"workPass1234"}
                """.formatted(name, active, loginId);
        return json(perform(post(MANAGERS), body)).at("/result/managerId").asText();
    }

    private String registerRecipient(String loginId, String name) throws Exception {
        userDomainService.registerUser(loginId, "elderPass1", name, Gender.FEMALE, LocalDate.of(1960, 3, 12), null, null);
        return json(perform(post(RECIPIENTS), "{\"loginId\":\"" + loginId + "\"}"))
                .at("/result/recipientPublicId").asText();
    }

    private static String assignBody(String managerId) {
        return "{\"managerId\":\"" + managerId + "\"}";
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, adminToken == null ? "" : adminToken);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
