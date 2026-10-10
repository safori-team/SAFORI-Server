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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    @DisplayName("기존 기관 역할에 대상자 수정 권한을 회수·재부여하면 PUT 인가에 즉시 반영된다")
    void manageRecipientUpdatePermission() throws Exception {
        String permissions = URL + "/" + happy + "/roles/ORG_ADMIN/permissions";
        String updatePermission = permissions + "/RECIPIENT_UPDATE";
        String adminToken = signIn("orgadmin01", "tempPass1234");
        String recipient = json(operator(post(URL + "/" + happy + "/care-recipients"), """
                {"name":"김영희","username":"elder001","password":"elderPass1","gender":"FEMALE","birthDate":"1960-03-12"}
                """)).at("/result/recipientPublicId").asText();
        String updateUrl = "/v1/api/admin/care-recipients/" + recipient;
        String updateBody = "{\"name\":\"김영희\",\"active\":true,\"loginId\":\"elder001\"}";

        operator(get(permissions), null)
                .andExpect(jsonPath("$.result.permissions").isArray());
        mockMvc.perform(delete(updatePermission)).andExpect(status().isUnauthorized());
        operator(delete(updatePermission), null)
                .andExpect(jsonPath("$.result.permissions[?(@ == 'RECIPIENT_UPDATE')]").isEmpty());
        mockMvc.perform(put(updateUrl).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isForbidden());

        operator(put(updatePermission), null)
                .andExpect(jsonPath("$.result.permissions[?(@ == 'RECIPIENT_UPDATE')]").isNotEmpty());
        operator(put(updatePermission), null).andExpect(status().isOk());
        mockMvc.perform(put(updateUrl).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk());
        operator(put(URL + "/" + happy + "/roles/CARE_WORKER/permissions/RAW_CONTENT_READ"), null)
                .andExpect(jsonPath("$.code").value(4401));
        operator(put(URL + "/" + happy + "/roles/NO_SUCH_ROLE/permissions/RECIPIENT_UPDATE"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4403));
    }

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

    @Test
    @DisplayName("테스트 계정: 운영자가 대상자(앱 계정+등록)·보호자(연결)·담당자를 만들면 각자 로그인되고 관리자 화면에 보인다")
    void registerMembers() throws Exception {
        String recipient = json(operator(post(URL + "/" + happy + "/care-recipients"), """
                {"name":"김영희","username":"elder001","password":"elderPass1","gender":"FEMALE","birthDate":"1960-03-12"}
                """)).at("/result/recipientPublicId").asText();
        operator(post(URL + "/" + happy + "/guardians"), """
                {"name":"김희영","phone":"010-3333-4444","careRecipientId":"%s","relation":"CHILD","active":true,
                 "loginId":"guard001","password":"guardPass1234"}
                """.formatted(recipient))
                .andExpect(jsonPath("$.result.careRecipient.name").value("김영희"));
        operator(post(URL + "/" + happy + "/managers"), """
                {"name":"박지현","phone":"010-2222-3333","jobTitle":"사회복지사","active":true,
                 "loginId":"worker001","password":"workPass1234"}
                """)
                .andExpect(jsonPath("$.result.loginId").value("worker001"));

        signIn("elder001", "elderPass1");
        signIn("guard001", "guardPass1234");
        signIn("worker001", "workPass1234");
        String adminToken = signIn("orgadmin01", "tempPass1234");
        mockMvc.perform(get("/v1/api/admin/care-recipients/" + recipient).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.result.name").value("김영희"))
                .andExpect(jsonPath("$.result.guardians[0].name").value("김희영"));
        operator(get(URL + "/" + happy + "/managers"), null)
                .andExpect(jsonPath("$.result.counts.total").value(1))
                .andExpect(jsonPath("$.result.managers.items[0].loginId").value("worker001"));
        operator(get(URL + "/" + happy + "/guardians"), null)
                .andExpect(jsonPath("$.result.guardians.items[0].loginId").value("guard001"))
                .andExpect(jsonPath("$.result.guardians.items[0].careRecipient.name").value("김영희"));
        operator(get(URL + "/" + happy), null)
                .andExpect(jsonPath("$.result.careWorkerCount").value(1))
                .andExpect(jsonPath("$.result.guardianCount").value(1))
                .andExpect(jsonPath("$.result.recipientCount").value(1));

        operator(post(URL + "/no-such-org/managers"), """
                {"name":"박지현","phone":"010-2222-3333","jobTitle":"사회복지사","active":true,
                 "loginId":"worker002","password":"workPass1234"}
                """).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("테스트 상태 변경: 즉시 확인으로 올리고, 관심으로 내리고(덮어쓰기 규칙 우회), 표시 없음으로 바꾼다")
    void changeRecipientStatus() throws Exception {
        String recipient = json(operator(post(URL + "/" + happy + "/care-recipients"), """
                {"name":"김영희","username":"elder001","password":"elderPass1","gender":"FEMALE"}
                """)).at("/result/recipientPublicId").asText();
        String status = URL + "/" + happy + "/care-recipients/" + recipient + "/status";
        String list = URL + "/" + happy + "/care-recipients";

        operator(get(list), null)
                .andExpect(jsonPath("$.result.counts.total").value(1))
                .andExpect(jsonPath("$.result.recipients.items[0].name").value("김영희"))
                .andExpect(jsonPath("$.result.recipients.items[0].loginId").value("elder001"))
                .andExpect(jsonPath("$.result.recipients.items[0].statusCode").doesNotExist());

        operator(put(status), "{\"statusCode\":\"URGENT\"}")
                .andExpect(jsonPath("$.result.scheduled").value(false))
                .andExpect(jsonPath("$.result.record.statusCode").value("URGENT"))
                .andExpect(jsonPath("$.result.record.reasonMessage").value("119에 SOS 요청을 했어요."))
                .andExpect(jsonPath("$.result.record.processingStatus").value("UNCHECKED"));
        operator(get(list), null).andExpect(jsonPath("$.result.counts.urgent").value(1));

        operator(put(status), "{\"statusCode\":\"INTEREST\",\"reasonMessage\":\"테스트 문구\"}")
                .andExpect(jsonPath("$.result.record.statusCode").value("INTEREST"))
                .andExpect(jsonPath("$.result.record.reasonMessage").value("테스트 문구"))
                .andExpect(jsonPath("$.result.record.current").value(true));

        // 5분 뒤 변경은 예약만 되고 지금 상태는 그대로다
        operator(put(status), "{\"statusCode\":\"URGENT\",\"delayMinutes\":5}")
                .andExpect(jsonPath("$.result.scheduled").value(true))
                .andExpect(jsonPath("$.result.appliesAt").exists())
                .andExpect(jsonPath("$.result.record").doesNotExist());
        operator(get(list), null).andExpect(jsonPath("$.result.counts.urgent").value(0));
        operator(put(status), "{\"statusCode\":\"URGENT\",\"delayMinutes\":61}")
                .andExpect(status().isBadRequest());
        operator(get(list).param("statusCode", "INTEREST"), null)
                .andExpect(jsonPath("$.result.recipients.totalElements").value(1));

        operator(put(status), "{\"statusCode\":null}").andExpect(jsonPath("$.isSuccess").value(true));
        operator(get(list), null)
                .andExpect(jsonPath("$.result.counts.interest").value(0))
                .andExpect(jsonPath("$.result.recipients.items[0].statusCode").doesNotExist());

        operator(put(URL + "/" + happy + "/care-recipients/no-such/status"), "{\"statusCode\":\"URGENT\"}")
                .andExpect(jsonPath("$.code").value(4454));
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
