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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 대상자 현황: 상태 코드 정렬·탭·개수, 담당자 범위, 처리 상태 변경(완료 시 X), 흡수된 기록.
 */
@SpringBootTest
@Transactional
class RecipientStatusBoardTest {

    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;

    private MockMvc mockMvc;
    private String token;
    private String adminToken;
    private String workerToken;
    private String e1;
    private String e2;
    private String e3;
    private String e4;
    private String absorbedRecordId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        adminToken = signIn("orgadmin01", "tempPass1234");
        token = adminToken;

        String worker = json(perform(post("/v1/api/admin/managers"), """
                {"name":"박지현","phone":"01012345678","jobTitle":"사회복지사","active":true,
                 "loginId":"worker001","password":"workPass1234"}
                """)).at("/result/managerId").asText();
        workerToken = signIn("worker001", "workPass1234");

        e1 = recipient("elder001", "홍길동");
        e2 = recipient("elder002", "김영희");
        e3 = recipient("elder003", "김철수");
        e4 = recipient("elder004", "박평온");
        perform(put(RECIPIENTS + "/" + e1 + "/manager"), "{\"managerId\":\"" + worker + "\"}");
        perform(put(RECIPIENTS + "/" + e2 + "/manager"), "{\"managerId\":\"" + worker + "\"}");

        raise(e1, "HELP_REQUEST", "어르신이 담당자와의 연결을 요청했어요.", LocalDateTime.now().minusMinutes(20));
        raise(e2, "SAME_EMOTION_REPEAT", "최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.", LocalDateTime.now().minusDays(1));
        absorbedRecordId = raise(e2, "COUNSEL_EXTENSION_REPEAT", "낮은 등급 사유", LocalDateTime.now());
        raise(e3, "COUNSEL_EXTENSION_REPEAT", "최근 상담 3회 중 2회에서 '조금 더 이야기하기'를 선택했어요.", LocalDateTime.now().minusDays(3));
    }

    @Test
    @DisplayName("관리자: 기관 전체가 상태 코드 높은 순으로 보이고, 낮은 등급은 흡수되어 현재 사유가 유지된다")
    void adminSeesWholeOrganizationSortedByStatus() throws Exception {
        perform(get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.counts.total").value(4))
                .andExpect(jsonPath("$.result.counts.assigned").value(2))
                .andExpect(jsonPath("$.result.counts.unassigned").value(2))
                .andExpect(jsonPath("$.result.counts.urgent").value(1))
                .andExpect(jsonPath("$.result.counts.caution").value(1))
                .andExpect(jsonPath("$.result.counts.interest").value(1))
                .andExpect(jsonPath("$.result.recipients.items[*].careRecipientId").value(contains(e1, e2, e3, e4)))
                .andExpect(jsonPath("$.result.recipients.items[1].reasonMessage").value("최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요."))
                .andExpect(jsonPath("$.result.recipients.items[0].manager.name").value("박지현"))
                .andExpect(jsonPath("$.result.recipients.items[3].statusCode").doesNotExist());

        perform(get(RECIPIENTS).param("statusCode", "CAUTION"), null)
                .andExpect(jsonPath("$.result.recipients.items[*].careRecipientId").value(contains(e2)));
        perform(get(RECIPIENTS).param("assignment", "UNASSIGNED"), null)
                .andExpect(jsonPath("$.result.recipients.items[*].careRecipientId").value(contains(e3, e4)));
        perform(get(RECIPIENTS).param("keyword", "지현"), null)
                .andExpect(jsonPath("$.result.recipients.items[*].careRecipientId").value(contains(e1, e2)));

        perform(get(RECIPIENTS + "/" + e2 + "/records/" + absorbedRecordId), null)
                .andExpect(jsonPath("$.result.processingStatus").value("ABSORBED"))
                .andExpect(jsonPath("$.result.current").value(false));
    }

    @Test
    @DisplayName("담당자: 본인 배정 대상자만 보인다")
    void workerSeesOnlyAssigned() throws Exception {
        token = workerToken;
        perform(get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.counts.total").value(2))
                .andExpect(jsonPath("$.result.recipients.items[*].careRecipientId").value(contains(e1, e2)));
    }

    @Test
    @DisplayName("처리 상태: 진행 중 → 완료하면 상태 코드 X, 완료된 기록은 다시 바꿀 수 없다(4456)")
    void processingToDoneClearsStatus() throws Exception {
        String recordId = json(perform(get(RECIPIENTS), null)).at("/result/recipients/items/0/recordId").asText();

        perform(patch(RECIPIENTS + "/" + e1 + "/records/" + recordId), "{\"processingStatus\":\"IN_PROGRESS\"}")
                .andExpect(jsonPath("$.result.processingStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.processedByName").value("이관리"));
        perform(patch(RECIPIENTS + "/" + e1 + "/records/" + recordId), "{\"processingStatus\":\"DONE\"}")
                .andExpect(status().isOk());

        perform(get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.counts.urgent").value(0))
                .andExpect(jsonPath("$.result.recipients.items[0].careRecipientId").value(e2));
        perform(patch(RECIPIENTS + "/" + e1 + "/records/" + recordId), "{\"processingStatus\":\"IN_PROGRESS\"}")
                .andExpect(jsonPath("$.code").value(4456));
    }

    @Test
    @DisplayName("같은 사유는 처리 중에 다시 감지돼도 무시(처리 상태 유지), 완료 후 다시 감지되면 새 기록")
    void sameReasonIgnoredWhileOpenAndRaisedAgainAfterDone() throws Exception {
        String first = json(perform(get(RECIPIENTS).param("statusCode", "CAUTION"), null))
                .at("/result/recipients/items/0/recordId").asText();
        perform(patch(RECIPIENTS + "/" + e2 + "/records/" + first), "{\"processingStatus\":\"IN_PROGRESS\"}");

        String again = raise(e2, "SAME_EMOTION_REPEAT", "다시 감지", LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(again).isEqualTo(first);
        perform(get(RECIPIENTS + "/" + e2 + "/records/" + first), null)
                .andExpect(jsonPath("$.result.processingStatus").value("IN_PROGRESS"));

        perform(patch(RECIPIENTS + "/" + e2 + "/records/" + first), "{\"processingStatus\":\"DONE\"}");
        String next = raise(e2, "SAME_EMOTION_REPEAT", "완료 후 다시 감지", LocalDateTime.now());
        org.assertj.core.api.Assertions.assertThat(next).isNotEqualTo(first);
        perform(get(RECIPIENTS + "/" + e2 + "/records/" + next), null)
                .andExpect(jsonPath("$.result.processingStatus").value("UNCHECKED"))
                .andExpect(jsonPath("$.result.reasonTitle").value("동일 감정 반복"))
                .andExpect(jsonPath("$.result.guidanceLabel").value("확인 권장"));
    }

    @Test
    @DisplayName("대상자 상세: 상태 코드가 있으면 확인 사유와 담당자, 없으면(X) 확인 사유 null")
    void recipientDetail() throws Exception {
        perform(get(RECIPIENTS + "/" + e2), null)
                .andExpect(jsonPath("$.result.name").value("김영희"))
                .andExpect(jsonPath("$.result.manager.name").value("박지현"))
                .andExpect(jsonPath("$.result.statusCode").value("CAUTION"))
                .andExpect(jsonPath("$.result.processingStatus").value("UNCHECKED"))
                .andExpect(jsonPath("$.result.reason.title").value("동일 감정 반복"))
                .andExpect(jsonPath("$.result.reason.guidanceLabel").value("확인 권장"))
                .andExpect(jsonPath("$.result.recentActions").isEmpty());

        perform(get(RECIPIENTS + "/" + e4), null)
                .andExpect(jsonPath("$.result.statusCode").doesNotExist())
                .andExpect(jsonPath("$.result.reason").doesNotExist())
                .andExpect(jsonPath("$.result.manager").doesNotExist());
    }

    private String raise(String recipientId, String reasonType, String message, LocalDateTime detectedAt) throws Exception {
        return json(perform(post("/v1/api/admin/dev/care-recipients/" + recipientId + "/records"),
                "{\"reasonType\":\"%s\",\"reasonMessage\":\"%s\",\"detectedAt\":\"%s\"}"
                        .formatted(reasonType, message, detectedAt.withNano(0))))
                .at("/result/recordId").asText();
    }

    private String recipient(String loginId, String name) throws Exception {
        userDomainService.registerUser(loginId, "elderPass1", name, Gender.FEMALE, LocalDate.of(1960, 3, 12), null, null);
        return json(perform(post(RECIPIENTS), "{\"loginId\":\"" + loginId + "\"}")).at("/result/recipientPublicId").asText();
    }

    private String signIn(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).at("/result/accessToken").asText();
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
