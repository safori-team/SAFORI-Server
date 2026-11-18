package com.safori.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 운영자 개발용 마음일기 API: 운영자 키 체인 아래에 있고, 사용자 JWT 없이 아이디로 대상을 고른다.
 */
@SpringBootTest
@Transactional
class OperatorDevMindDiaryApiTest {

    private static final String BASE = "/v1/api/operator/dev/users/nobody";
    private static final String KEY = "test-only-operator-key";

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    @DisplayName("운영자 키가 없으면 401")
    void requiresOperatorKey() throws Exception {
        mockMvc.perform(get(BASE + "/mind-diaries")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/mind-diary-offer")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("운영자 키가 맞으면 통과하고, 없는 사용자는 4052")
    void unknownUser() throws Exception {
        mockMvc.perform(get(BASE + "/mind-diaries").header("X-Operator-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4052));
        mockMvc.perform(post(BASE + "/mind-diary-offer").header("X-Operator-Key", KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4052));
    }

    @Test
    @DisplayName("조회 기간은 1~31일")
    void daysIsBounded() throws Exception {
        mockMvc.perform(get(BASE + "/mind-diaries").param("days", "32").header("X-Operator-Key", KEY))
                .andExpect(status().isBadRequest());
    }
}
