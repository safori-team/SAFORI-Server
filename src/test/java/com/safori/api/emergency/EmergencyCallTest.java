package com.safori.api.emergency;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 긴급 전화: 어르신이 누르면 복지관 대상자 현황에 즉시 확인(도움 요청)이 올라간다.
 */
@SpringBootTest
@Transactional
class EmergencyCallTest {

    private static final String URL = "/v1/api/emergency-calls";
    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String admin;
    private String worker;
    private String recipient;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        admin = signIn("orgadmin01", "tempPass1234");

        userDomainService.registerUser("elder001", "elderPass1", "홍길동", Gender.MALE, LocalDate.of(1960, 3, 12), null, null);
        userDomainService.registerUser("elder002", "elderPass1", "미등록", Gender.MALE, LocalDate.of(1960, 3, 12), null, null);
        recipient = json(call(admin, post(RECIPIENTS), "{\"loginId\":\"elder001\"}")).at("/result/recipientPublicId").asText();
        String managerId = json(call(admin, post("/v1/api/admin/managers"), """
                {"name":"박지현","phone":"01012345678","jobTitle":"사회복지사","active":true,
                 "loginId":"worker001","password":"workPass1234"}
                """)).at("/result/managerId").asText();
        call(admin, post(RECIPIENTS + "/" + recipient + "/manager"), "{\"managerId\":\"" + managerId + "\"}");
        worker = signIn("worker001", "workPass1234");
    }

    @Test
    @DisplayName("등록된 어르신이 누르면 대상자 현황에 즉시 확인이 올라가고, 다시 누르면 새 기록(미확인)이 된다")
    void notifiesOrganization() throws Exception {
        String elder = signIn("elder001", "elderPass1");
        call(elder, post(URL), null).andExpect(jsonPath("$.result.notified").value(true));

        JsonNode first = json(call(admin, get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.counts.urgent").value(1))
                .andExpect(jsonPath("$.result.recipients.items[0].statusCode").value("URGENT"))
                .andExpect(jsonPath("$.result.recipients.items[0].reasonMessage").value("119에 SOS 요청을 했어요."))
                .andExpect(jsonPath("$.result.recipients.items[0].processingStatus").value("UNCHECKED")));
        String firstRecord = first.at("/result/recipients/items/0/recordId").asText();
        call(worker, patch(RECIPIENTS + "/" + recipient + "/records/" + firstRecord), "{\"processingStatus\":\"IN_PROGRESS\"}");

        call(elder, post(URL), null).andExpect(jsonPath("$.result.notified").value(true));
        JsonNode second = json(call(admin, get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.counts.urgent").value(1))
                .andExpect(jsonPath("$.result.recipients.items[0].processingStatus").value("UNCHECKED")));
        assertThat(second.at("/result/recipients/items/0/recordId").asText()).isNotEqualTo(firstRecord);
    }

    @Test
    @DisplayName("기관에 등록되지 않은 어르신은 notified=false, 비로그인·백오피스 토큰은 통하지 않는다")
    void unregisteredAndUnauthorized() throws Exception {
        call(signIn("elder002", "elderPass1"), post(URL), null)
                .andExpect(jsonPath("$.result.notified").value(false));
        call(admin, get(RECIPIENTS), null).andExpect(jsonPath("$.result.counts.urgent").value(0));

        mockMvc.perform(post(URL)).andExpect(status().is4xxClientError());
        call(admin, post(URL), null).andExpect(status().is4xxClientError());
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
