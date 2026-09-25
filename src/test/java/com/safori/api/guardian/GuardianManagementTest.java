package com.safori.api.guardian;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 보호자: 등록(연결 포함/미포함), 목록 탭·검색, 상세, 수정, 연결(관계)·해제, 한 대상자에만 연결.
 */
@SpringBootTest
@Transactional
class GuardianManagementTest {

    private static final String GUARDIANS = "/v1/api/admin/guardians";
    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String token;
    private String younghee;
    private String gildong;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        token = "Bearer " + signIn("orgadmin01", "tempPass1234").at("/result/accessToken").asText();
        younghee = recipient("elder001", "김영희");
        gildong = recipient("elder002", "홍길동");
    }

    @Test
    @DisplayName("대상자를 골라 등록하면 바로 연결되고, 목록·상세·대상자 상세에 관계와 함께 보인다")
    void registerWithLink() throws Exception {
        String guardianId = register("guard001", "김희영", younghee, "CHILD", null)
                .andExpect(jsonPath("$.result.careRecipient.name").value("김영희"))
                .andExpect(jsonPath("$.result.careRecipient.relationLabel").value("자녀"))
                .andReturn().getResponse().getContentAsString().transform(this::guardianId);
        register("guard002", "김희순", null, null, null);

        perform(get(GUARDIANS), null)
                .andExpect(jsonPath("$.result.counts.total").value(2))
                .andExpect(jsonPath("$.result.counts.linked").value(1))
                .andExpect(jsonPath("$.result.counts.unlinked").value(1))
                .andExpect(jsonPath("$.result.guardians.items[0].name").value("김희순"))
                .andExpect(jsonPath("$.result.guardians.items[0].linked").value(false))
                .andExpect(jsonPath("$.result.guardians.items[1].careRecipient.name").value("김영희"));
        perform(get(GUARDIANS).param("status", "UNLINKED"), null)
                .andExpect(jsonPath("$.result.guardians.totalElements").value(1));
        perform(get(GUARDIANS).param("keyword", "영희"), null)
                .andExpect(jsonPath("$.result.guardians.items[0].guardianId").value(guardianId));

        perform(get(GUARDIANS + "/" + guardianId), null)
                .andExpect(jsonPath("$.result.loginId").value("guard001"))
                .andExpect(jsonPath("$.result.phone").value("01033334444"))
                .andExpect(jsonPath("$.result.joinedAt").exists())
                .andExpect(jsonPath("$.result.careRecipient.birthDate").value("1960-03-12"));
        perform(get(RECIPIENTS + "/" + younghee), null)
                .andExpect(jsonPath("$.result.guardians[0].name").value("김희영"))
                .andExpect(jsonPath("$.result.guardians[0].relationLabel").value("자녀"));

        signIn("guard001", "guardPass1234");
    }

    @Test
    @DisplayName("연결(기타 관계) → 다른 대상자 연결은 4459 → 해제 후 연결")
    void linkAndUnlink() throws Exception {
        String guardianId = register("guard001", "김희영", null, null, null)
                .andReturn().getResponse().getContentAsString().transform(this::guardianId);

        perform(put(RECIPIENTS + "/" + younghee + "/guardians"),
                "{\"guardianId\":\"" + guardianId + "\",\"relation\":\"OTHER\",\"relationText\":\"며느리\"}")
                .andExpect(jsonPath("$.result.careRecipient.relation").value("OTHER"))
                .andExpect(jsonPath("$.result.careRecipient.relationLabel").value("며느리"));
        perform(put(RECIPIENTS + "/" + gildong + "/guardians"),
                "{\"guardianId\":\"" + guardianId + "\",\"relation\":\"CHILD\"}")
                .andExpect(jsonPath("$.code").value(4459));

        perform(delete(RECIPIENTS + "/" + younghee + "/guardians/" + guardianId), null)
                .andExpect(jsonPath("$.result.careRecipient").doesNotExist());
        perform(put(RECIPIENTS + "/" + gildong + "/guardians"),
                "{\"guardianId\":\"" + guardianId + "\",\"relation\":\"CHILD\"}")
                .andExpect(jsonPath("$.result.careRecipient.name").value("홍길동"));
    }

    @Test
    @DisplayName("수정: 이름·연락처·비활성화. 대상자를 골랐는데 관계가 없으면 400, 없는 보호자는 4307")
    void updateAndValidation() throws Exception {
        String guardianId = register("guard001", "김희영", null, null, null)
                .andReturn().getResponse().getContentAsString().transform(this::guardianId);
        perform(put(GUARDIANS + "/" + guardianId), "{\"name\":\"김희영2\",\"phone\":\"010-5555-6666\",\"active\":false}")
                .andExpect(jsonPath("$.result.name").value("김희영2"))
                .andExpect(jsonPath("$.result.phone").value("01055556666"))
                .andExpect(jsonPath("$.result.active").value(false));

        register("guard002", "김희순", younghee, null, null).andExpect(status().isBadRequest());
        register("guard003", "김희순", younghee, "OTHER", null).andExpect(status().isBadRequest());

        perform(get(GUARDIANS + "/no-such-guardian"), null).andExpect(jsonPath("$.code").value(4307));
    }

    private ResultActions register(String loginId, String name, String recipientId, String relation,
                                   String relationText) throws Exception {
        String body = """
                {"name":"%s","phone":"010-3333-4444","active":true,"loginId":"%s","password":"guardPass1234"%s%s%s}
                """.formatted(name, loginId,
                recipientId == null ? "" : ",\"careRecipientId\":\"" + recipientId + "\"",
                relation == null ? "" : ",\"relation\":\"" + relation + "\"",
                relationText == null ? "" : ",\"relationText\":\"" + relationText + "\"");
        return perform(post(GUARDIANS), body);
    }

    private String recipient(String loginId, String name) throws Exception {
        userDomainService.registerUser(loginId, "elderPass1", name, Gender.FEMALE, LocalDate.of(1960, 3, 12), null, null);
        return objectMapper.readTree(perform(post(RECIPIENTS), "{\"loginId\":\"" + loginId + "\"}")
                .andReturn().getResponse().getContentAsString()).at("/result/recipientPublicId").asText();
    }

    private JsonNode signIn(String loginId, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String guardianId(String body) {
        try {
            return objectMapper.readTree(body).at("/result/guardianId").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }
}
