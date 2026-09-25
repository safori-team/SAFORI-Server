package com.safori.api.journal;

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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 일지: 폼(하위 항목 트리), 등록(폼 규칙 검사·작성 당시 상태 스냅샷), 상세(섹션별 트리), 대상자 상세의 최근 조치 기록.
 */
@SpringBootTest
@Transactional
class CareJournalTest {

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
        String body = mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"orgadmin01\",\"password\":\"tempPass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + objectMapper.readTree(body).at("/result/accessToken").asText();

        userDomainService.registerUser("elder001", "elderPass1", "김영희", Gender.FEMALE, LocalDate.of(1960, 3, 12), null, null);
        recipient = json(perform(post(RECIPIENTS), "{\"loginId\":\"elder001\"}")).at("/result/recipientPublicId").asText();
        perform(post("/v1/api/admin/dev/care-recipients/" + recipient + "/records"),
                "{\"reasonType\":\"SAME_EMOTION_REPEAT\",\"reasonMessage\":\"최근 일기 3건 중 2건에서 슬픔 계열 감정이 반복됐어요.\"}");
    }

    @Test
    @DisplayName("폼: 섹션 순서와 하위 항목(정서 변화 → 6개)")
    void form() throws Exception {
        perform(get("/v1/api/admin/journal-form"), null)
                .andExpect(jsonPath("$.result.groups[*].code").value(contains("METHOD", "RESULT", "CONDITION", "ACTION", "FOLLOW_UP")))
                .andExpect(jsonPath("$.result.groups[2].options[1].code").value("EMOTION_CHANGE"))
                .andExpect(jsonPath("$.result.groups[2].options[1].children.length()").value(6))
                .andExpect(jsonPath("$.result.groups[2].options[0].exclusive").value(true));
    }

    @Test
    @DisplayName("등록 → 상세(섹션별 트리·작성 당시 상태) → 대상자 상세 최근 조치 기록·목록 최근 안부 확인")
    void writeAndRead() throws Exception {
        String journalId = json(perform(post(RECIPIENTS + "/" + recipient + "/journals"), journal("""
                {"optionCode":"VISIT"},{"optionCode":"CONTACTED"},
                {"optionCode":"EMOTION_CHANGE"},{"optionCode":"LONELINESS"},{"optionCode":"EMOTION_OTHER","text":"자녀 이야기에 눈물"},
                {"optionCode":"WELLBEING_CHECKED"},{"optionCode":"GUARDIAN_CONTACT_NEEDED"}
                """)).andExpect(status().isOk())).at("/result/journalId").asText();

        perform(get(RECIPIENTS + "/" + recipient + "/journals/" + journalId), null)
                .andExpect(jsonPath("$.result.recipientName").value("김영희"))
                .andExpect(jsonPath("$.result.writerName").value("이관리"))
                .andExpect(jsonPath("$.result.statusCode").value("CAUTION"))
                .andExpect(jsonPath("$.result.processingStatus").value("UNCHECKED"))
                .andExpect(jsonPath("$.result.writtenAt").exists())
                .andExpect(jsonPath("$.result.guardianVisible").value(true))
                .andExpect(jsonPath("$.result.sections[*].code").value(contains("METHOD", "RESULT", "CONDITION", "ACTION", "FOLLOW_UP")))
                .andExpect(jsonPath("$.result.sections[2].items[0].label").value("정서 변화"))
                .andExpect(jsonPath("$.result.sections[2].items[0].children[*].label").value(contains("외로움 표현", "기타")))
                .andExpect(jsonPath("$.result.sections[2].items[0].children[1].text").value("자녀 이야기에 눈물"));

        perform(get(RECIPIENTS + "/" + recipient), null)
                .andExpect(jsonPath("$.result.recentActions[0].journalId").value(journalId))
                .andExpect(jsonPath("$.result.recentActions[0].method").value("방문"))
                .andExpect(jsonPath("$.result.recentActions[0].result").value("연락됨"))
                .andExpect(jsonPath("$.result.recentActions[0].actions[0]").value("안부 확인 완료"))
                .andExpect(jsonPath("$.result.recentActions[0].followUps[0]").value("보호자 연락 필요"));
        perform(get(RECIPIENTS), null)
                .andExpect(jsonPath("$.result.recipients.items[0].lastCheckedAt").value("2026-09-13T14:00:00"));
    }

    @Test
    @DisplayName("폼 규칙 위반은 4457: 단일 섹션 2개, 특이사항 없음+다른 상태, 부모 없는 하위 항목, 기타 입력 누락, 필수 섹션 누락")
    void invalidSelections() throws Exception {
        for (String selections : new String[]{
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"PHONE\"},{\"optionCode\":\"CONTACTED\"},{\"optionCode\":\"NO_ISSUE\"}",
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"CONTACTED\"},{\"optionCode\":\"NO_ISSUE\"},{\"optionCode\":\"SLEEP_CHANGE\"}",
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"CONTACTED\"},{\"optionCode\":\"LONELINESS\"}",
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"CONTACTED\"},{\"optionCode\":\"CONDITION_OTHER\"}",
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"CONTACTED\"}",
                "{\"optionCode\":\"VISIT\"},{\"optionCode\":\"CONTACTED\"},{\"optionCode\":\"NO_SUCH\"}"}) {
            perform(post(RECIPIENTS + "/" + recipient + "/journals"), journal(selections))
                    .andExpect(jsonPath("$.code").value(4457));
        }
    }

    private static String journal(String selections) {
        return """
                {"confirmedAt":"2026-09-13T14:00:00","memo":"다음 주 재방문","guardianVisible":true,
                 "selections":[%s]}
                """.formatted(selections);
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
