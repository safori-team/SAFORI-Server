package com.safori.security;

import com.safori.api.user.dto.UserInfoResponse;
import com.safori.api.user.service.GetUserInfoUseCase;
import com.safori.api.user.service.SignUpUseCase;
import com.safori.security.service.UserTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
class SecurityFilterChainTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    @MockBean UserTokenService userTokenService;
    @MockBean GetUserInfoUseCase getUserInfoUseCase;
    @MockBean SignUpUseCase signUpUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    @DisplayName("보호 엔드포인트 - 토큰 없이 접근 시 거부(4xx)")
    void protectedEndpoint_withoutToken_denied() throws Exception {
        mockMvc.perform(get("/v1/api/users"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("보호 엔드포인트 - 유효 토큰이면 200, 인증 컨텍스트로 내 정보 조회")
    void protectedEndpoint_withValidToken_ok() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                new User("user01", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        given(userTokenService.getAuthentication(anyString())).willReturn(authentication);
        given(getUserInfoUseCase.execute("user01"))
                .willReturn(UserInfoResponse.builder().username("user01").name("홍길동").build());

        mockMvc.perform(get("/v1/api/users").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("user01"))
                .andExpect(jsonPath("$.result.name").value("홍길동"));
    }
}
