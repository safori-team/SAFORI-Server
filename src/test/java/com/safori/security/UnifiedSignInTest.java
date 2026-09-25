package com.safori.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.api.operator.dto.CreateOrganizationRequest;
import com.safori.api.operator.service.CreateOrganizationUseCase;
import com.safori.domain.account.exception.AccountHandler;
import com.safori.domain.account.service.BackofficeAccountDomainService;
import com.safori.domain.user.exception.UserHandler;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 통합 로그인: 어르신·백오피스 계정이 같은 sign-in/reissue를 쓰고, 내 정보 조회는 두 토큰을 모두 받는다.
 */
@SpringBootTest
@Transactional
class UnifiedSignInTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired ObjectMapper objectMapper;
    @Autowired CreateOrganizationUseCase createOrganizationUseCase;
    @Autowired UserDomainService userDomainService;
    @Autowired BackofficeAccountDomainService accountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        createOrganizationUseCase.execute(CreateOrganizationRequest.builder()
                .organizationName("사포리 복지관").adminLoginId("orgadmin01")
                .adminPassword("tempPass1234").adminName("이관리").build());
        userDomainService.registerUser("elder01", "elderPass1", "김순자", null, null);
    }

    @Test
    @DisplayName("백오피스 계정: 로그인 → role·권한·기관이 담긴 내 정보 → refresh 회전 재발급")
    void backofficeAccountSignsInThroughSameEndpoint() throws Exception {
        JsonNode token = signIn("orgadmin01", "tempPass1234");
        assertThat(token.get("role").asText()).isEqualTo("ORG_ADMIN");

        mockMvc.perform(get("/v1/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("ORG_ADMIN"))
                .andExpect(jsonPath("$.result.username").value("orgadmin01"))
                .andExpect(jsonPath("$.result.name").value("이관리"))
                .andExpect(jsonPath("$.result.organization.name").value("사포리 복지관"))
                .andExpect(jsonPath("$.result.permissions[?(@ == 'MEMBER_MANAGE')]").exists())
                .andExpect(jsonPath("$.result.permissions[?(@ == 'ROLE_ORG_ADMIN')]").doesNotExist());

        String refreshToken = token.get("refreshToken").asText();
        JsonNode reissued = reissue(refreshToken);
        assertThat(reissued.get("role").asText()).isEqualTo("ORG_ADMIN");
        mockMvc.perform(post("/v1/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("백오피스 토큰은 내 정보 조회에서만 통하고 다른 어르신 API에는 통하지 않는다")
    void backofficeTokenIsLimitedToUserInfo() throws Exception {
        JsonNode token = signIn("orgadmin01", "tempPass1234");

        mockMvc.perform(get("/v1/api/users/voices/recent").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("어르신: 같은 로그인으로 role=ELDER, 내 정보는 권한·기관 없이 기존 필드 그대로")
    void elderSignsInThroughSameEndpoint() throws Exception {
        JsonNode token = signIn("elder01", "elderPass1");
        assertThat(token.get("role").asText()).isEqualTo("ELDER");

        mockMvc.perform(get("/v1/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("ELDER"))
                .andExpect(jsonPath("$.result.username").value("elder01"))
                .andExpect(jsonPath("$.result.permissions").isEmpty());

        assertThat(reissue(token.get("refreshToken").asText()).get("role").asText()).isEqualTo("ELDER");
    }

    @Test
    @DisplayName("아이디는 어르신·백오피스 계정을 통틀어 유일하다")
    void loginIdIsGloballyUnique() {
        assertThatThrownBy(() -> userDomainService.registerUser("orgadmin01", "elderPass1", "중복", null, null))
                .isEqualTo(UserHandler.USERNAME_ALREADY_EXISTS);
        assertThatThrownBy(() -> accountService.register("elder01", "tempPass1234", "중복"))
                .isEqualTo(AccountHandler.LOGIN_ID_ALREADY_EXISTS);
    }

    private JsonNode signIn(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/api/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("result");
    }

    private JsonNode reissue(String refreshToken) throws Exception {
        String body = mockMvc.perform(post("/v1/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("result");
    }

    private static String bearer(JsonNode token) {
        return "Bearer " + token.get("accessToken").asText();
    }
}
