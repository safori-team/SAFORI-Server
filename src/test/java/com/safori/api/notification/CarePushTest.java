package com.safori.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.port.PushNotificationSender;
import com.safori.api.notification.port.PushSendResult;
import com.safori.api.notification.service.SendCarePushUseCase;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import com.safori.common.event.CareUrgentEnteredEvent;
import com.safori.domain.care.entity.CareReasonType;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.domain.care.service.CareRecordDomainService;
import com.safori.domain.notification.entity.CareNotification;
import com.safori.domain.notification.entity.CareNotificationType;
import com.safori.domain.notification.repository.CareNotificationRepository;
import com.safori.domain.user.entity.Gender;
import com.safori.domain.user.service.UserDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 복지관 알림: 관리자·담당자·보호자 기기 등록, 즉시 확인 진입 푸시, 주의 48시간 미확인 푸시.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class CarePushTest {

    private static final String TOKENS = "/v1/api/admin/device-tokens";
    private static final String RECIPIENTS = "/v1/api/admin/care-recipients";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;
    @Autowired SendCarePushUseCase sendCarePushUseCase;
    @Autowired CareRecordDomainService recordDomainService;
    @Autowired CareRecipientRepository recipientRepository;
    @Autowired CareNotificationRepository notificationRepository;
    @Autowired ApplicationEvents events;
    @MockBean PushNotificationSender pushNotificationSender;

    private MockMvc mockMvc;
    private String admin;
    private String worker;
    private String guardian;
    private String elder;
    private String recipient;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        when(pushNotificationSender.send(anyList(), any())).thenAnswer(invocation ->
                new PushSendResult(invocation.<List<String>>getArgument(0).size(), 0, List.of()));

        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("행복복지관").adminLoginId("orgadmin01").adminPassword("tempPass1234")
                .adminName("이관리").adminPhone("01011112222").build());
        admin = signIn("/v1/api/auth/sign-in", "orgadmin01", "tempPass1234");

        userDomainService.registerUser("elder001", "elderPass1", "홍길동", Gender.MALE, LocalDate.of(1960, 3, 12), null, null);
        elder = signIn("/v1/api/auth/sign-in", "elder001", "elderPass1");
        recipient = json(call(admin, post(RECIPIENTS), "{\"loginId\":\"elder001\"}")).at("/result/recipientPublicId").asText();
        String managerId = json(call(admin, post("/v1/api/admin/managers"), """
                {"name":"박지현","phone":"01012345678","jobTitle":"사회복지사","active":true,
                 "loginId":"worker001","password":"workPass1234"}
                """)).at("/result/managerId").asText();
        call(admin, post(RECIPIENTS + "/" + recipient + "/manager"), "{\"managerId\":\"" + managerId + "\"}");
        worker = signIn("/v1/api/auth/sign-in", "worker001", "workPass1234");
        call(admin, post("/v1/api/admin/guardians"), """
                {"name":"김희영","phone":"010-3333-4444","active":true,"loginId":"guard001","password":"guardPass1234",
                 "careRecipientId":"%s","relation":"CHILD"}
                """.formatted(recipient)).andExpect(status().isOk());
        guardian = signIn("/v1/api/auth/sign-in", "guard001", "guardPass1234");

        registerToken(admin, "admin-token");
        registerToken(worker, "worker-token");
        registerToken(guardian, "guardian-token");
    }

    @Test
    @DisplayName("즉시 확인으로 올라갈 때만 이벤트가 나가고, 관리자·담당자·보호자에게 한 번씩 보내 이력을 남긴다")
    void urgentEntered() throws Exception {
        call(elder, post("/v1/api/emergency-calls"), null).andExpect(jsonPath("$.result.notified").value(true));
        call(elder, post("/v1/api/emergency-calls"), null); // 이미 즉시 확인 — 다시 알리지 않는다
        List<CareUrgentEnteredEvent> raised = events.stream(CareUrgentEnteredEvent.class).toList();
        assertThat(raised).hasSize(1);

        sendCarePushUseCase.execute(raised.get(0).recordId(), CareNotificationType.URGENT_ENTERED);
        sendCarePushUseCase.execute(raised.get(0).recordId(), CareNotificationType.URGENT_ENTERED); // 중복 실행

        assertThat(sentTokens()).containsExactlyInAnyOrder("admin-token", "worker-token", "guardian-token");
        assertThat(notificationRepository.findAll())
                .hasSize(3)
                .allSatisfy(notification -> {
                    assertThat(notification.getTitle()).isEqualTo("홍길동 어르신이 도움을 요청했어요");
                    assertThat(notification.getSuccessCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("주의가 48시간 미확인이면 담당자·보호자에게만 한 번 보낸다. 48시간 전이거나 조치중이면 보내지 않는다")
    void cautionUnchecked() throws Exception {
        var record = recordDomainService.raise(recipientRepository.findByPublicId(recipient).orElseThrow(),
                CareReasonType.SAME_EMOTION_REPEAT, "최근 일기 3건 중 2건이 슬픔이에요.", LocalDateTime.now().minusHours(49));
        assertThat(sendCarePushUseCase.cautionUncheckedRecordIds(LocalDateTime.now().minusHours(2))).isEmpty();

        // 스케줄러가 하는 일. @SchedulerLock 메서드는 잠금이 테스트 트랜잭션을 커밋해 버려 직접 부르지 않는다.
        for (int run = 0; run < 2; run++) { // 이미 보낸 기록은 다시 잡히지 않는다
            for (Long recordId : sendCarePushUseCase.cautionUncheckedRecordIds(LocalDateTime.now())) {
                sendCarePushUseCase.execute(recordId, CareNotificationType.CAUTION_UNCHECKED_48H);
            }
        }

        assertThat(sentTokens()).containsExactlyInAnyOrder("worker-token", "guardian-token");
        assertThat(notificationRepository.findAll()).extracting(CareNotification::getBody)
                .containsExactlyInAnyOrder("아직 상태가 확인되지 않았어요. 안부를 확인해 주세요.", "아직 상태가 확인되지 않았어요.");

        notificationRepository.deleteAll();
        call(worker, patch(RECIPIENTS + "/" + recipient + "/records/" + record.getPublicId()),
                "{\"processingStatus\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        assertThat(sendCarePushUseCase.cautionUncheckedRecordIds(LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("기기 등록: 같은 기기에 다른 계정이 로그인하면 소유자가 바뀌고, 삭제하면 보내지 않는다. 어르신 토큰은 통하지 않는다")
    void deviceTokens() throws Exception {
        registerToken(worker, "admin-token"); // 관리자 기기에서 담당자가 로그인
        call(guardian, delete(TOKENS).param("token", "guardian-token"), null).andExpect(status().isOk());
        call(guardian, delete(TOKENS).param("token", "worker-token"), null).andExpect(status().isOk()); // 남의 토큰은 무시
        call(elder, post(TOKENS), "{\"token\":\"elder-token\"}").andExpect(status().is4xxClientError());

        call(elder, post("/v1/api/emergency-calls"), null);
        sendCarePushUseCase.execute(events.stream(CareUrgentEnteredEvent.class).findFirst().orElseThrow().recordId(),
                CareNotificationType.URGENT_ENTERED);

        assertThat(sentTokens()).containsExactlyInAnyOrder("admin-token", "worker-token");
        verify(pushNotificationSender, times(1)).send(anyList(), any()); // 담당자 한 명에게 기기 두 대
        assertThat(notificationRepository.findAll()).hasSize(3); // 기기가 없어도 이력은 남는다(성공 0)
    }

    @Test
    @DisplayName("관심·주의로 올라가는 것은 바로 알리지 않는다")
    void noPushBelowUrgent() {
        recordDomainService.raise(recipientRepository.findByPublicId(recipient).orElseThrow(),
                CareReasonType.SAME_EMOTION_REPEAT, "최근 일기 3건 중 2건이 슬픔이에요.", LocalDateTime.now());
        assertThat(events.stream(CareUrgentEnteredEvent.class)).isEmpty();
        verify(pushNotificationSender, never()).send(anyList(), any());
    }

    private List<String> sentTokens() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationSender, org.mockito.Mockito.atLeast(0)).send(tokens.capture(), any(PushMessage.class));
        return tokens.getAllValues().stream().flatMap(List::stream).toList();
    }

    private void registerToken(String bearer, String token) throws Exception {
        call(bearer, post(TOKENS), "{\"token\":\"" + token + "\"}").andExpect(status().isOk());
    }

    private String signIn(String url, String loginId, String password) throws Exception {
        return "Bearer " + json(mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + loginId + "\",\"password\":\"" + password + "\"}")))
                .at("/result/accessToken").asText();
    }

    private ResultActions call(String bearer, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
