package com.safori.api.recipient;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 대상자 정보 상세(기본 정보·담당자 직종·연락처)와 정보 수정(아이디·비밀번호·이용 상태).
 */
@SpringBootTest
@Transactional
class RecipientProfileTest {

    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String token;
    private String recipient;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        token = "Bearer " + signIn("orgadmin01", "tempPass1234").at("/result/accessToken").asText();
        userDomainService.registerUser("elder001", "elderPass1", "김영희", Gender.FEMALE,
                LocalDate.of(1960, 3, 12), "01012345678", null);
        recipient = json(perform(post(RECIPIENTS), "{\"loginId\":\"elder001\"}")).at("/result/recipientPublicId").asText();
    }

    @Test
    @DisplayName("상세: 연락처·아이디·이용 상태·가입일, 담당자 직종·연락처, 보호자 없음")
    void detail() throws Exception {
        String managerId = json(perform(post("/v1/api/admin/managers"), """
                {"name":"김철수","phone":"010-2222-3333","jobTitle":"사회복지사","active":true,
                 "loginId":"worker01","password":"workPass1234"}
                """)).at("/result/managerId").asText();
        perform(post(RECIPIENTS + "/" + recipient + "/manager"), "{\"managerId\":\"" + managerId + "\"}");

        perform(get(RECIPIENTS + "/" + recipient), null)
                .andExpect(jsonPath("$.result.phone").value("01012345678"))
                .andExpect(jsonPath("$.result.loginId").value("elder001"))
                .andExpect(jsonPath("$.result.active").value(true))
                .andExpect(jsonPath("$.result.joinedAt").exists())
                .andExpect(jsonPath("$.result.manager.jobTitle").value("사회복지사"))
                .andExpect(jsonPath("$.result.manager.phone").value("01022223333"))
                .andExpect(jsonPath("$.result.guardians").isEmpty());
    }

    @Test
    @DisplayName("수정: 기본 정보·아이디·비밀번호. 앱 리프레시 토큰은 새 아이디로 이어지고 새 아이디·비밀번호로 로그인된다")
    void update() throws Exception {
        String refreshToken = signIn("elder001", "elderPass1").at("/result/refreshToken").asText();

        perform(put(RECIPIENTS + "/" + recipient), """
                {"name":"김영희2","phone":"010-9999-8888","birthDate":"1961-01-02","active":true,
                 "loginId":"elder009","password":"newPass1234"}
                """)
                .andExpect(jsonPath("$.result.name").value("김영희2"))
                .andExpect(jsonPath("$.result.phone").value("01099998888"))
                .andExpect(jsonPath("$.result.birthDate").value("1961-01-02"))
                .andExpect(jsonPath("$.result.loginId").value("elder009"));

        mockMvc.perform(post("/v1/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(jsonPath("$.isSuccess").value(true));
        signIn("elder009", "newPass1234");
        mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"elder001\",\"password\":\"elderPass1\"}"))
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("비활성화하면 현황·목록에서 빠지고 상세는 열린다. 다른 계정 아이디로 바꾸면 4050")
    void deactivateAndDuplicateLoginId() throws Exception {
        perform(put(RECIPIENTS + "/" + recipient), """
                {"name":"김영희","active":false,"loginId":"elder001"}
                """)
                .andExpect(jsonPath("$.result.active").value(false))
                .andExpect(jsonPath("$.result.phone").doesNotExist());
        perform(get(RECIPIENTS), null).andExpect(jsonPath("$.result.counts.total").value(0));

        perform(put(RECIPIENTS + "/" + recipient), """
                {"name":"김영희","active":true,"loginId":"orgadmin01"}
                """)
                .andExpect(jsonPath("$.code").value(4050));
    }

    private JsonNode signIn(String loginId, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
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
