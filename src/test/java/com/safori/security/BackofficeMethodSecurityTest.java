package com.safori.security;

import com.safori.domain.access.policy.BackofficeActorResolver;
import com.safori.domain.organization.entity.OrganizationMember;
import com.safori.security.BackofficeFixture.Scenario;
import com.safori.security.BackofficeSecurityTestConfig.SampleRecipientUseCase;
import com.safori.security.filter.BackofficeAuthenticationFilter;
import com.safori.security.service.BackofficeTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static com.safori.security.BackofficeSecurityTestConfig.METHOD_SECURED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * UseCase에 거는 메서드 보안: {@code @PreAuthorize("@backofficeAccessPolicy...")}가 Spring 프록시에서 동작하는지 검증한다.
 * 예시 UseCase는 {@link BackofficeSecurityTestConfig.SampleRecipientUseCase}.
 */
@SpringBootTest
@Transactional
@Import(BackofficeSecurityTestConfig.class)
class BackofficeMethodSecurityTest {

    @Autowired SampleRecipientUseCase useCase;
    @Autowired BackofficeFixture fixture;
    @Autowired BackofficeActorResolver actorResolver;
    @Autowired BackofficeTokenService tokenService;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;

    private Scenario s;

    @BeforeEach
    void setUp() {
        s = fixture.scenario();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("어르신 단위: 담당자는 배정된 어르신만, 관리자는 소속 기관 어르신 모두")
    void recipientLevelPreAuthorize() {
        signInAs(s.worker());
        assertThat(useCase.readRecipient(s.recipient().getId())).isEqualTo("recipient:" + s.recipient().getId());
        assertThatThrownBy(() -> useCase.readRecipient(s.otherRecipient().getId()))
                .isInstanceOf(AccessDeniedException.class);

        signInAs(s.admin());
        assertThat(useCase.readRecipient(s.otherRecipient().getId())).isNotNull();
        assertThatThrownBy(() -> useCase.readRecipient(s.foreignRecipient().getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("기관 단위: 대상자 등록은 관리자가 자기 기관에서만 할 수 있다")
    void organizationLevelPreAuthorize() {
        signInAs(s.admin());
        assertThat(useCase.registerRecipient(s.organization().getId())).isEqualTo("registered");
        assertThatThrownBy(() -> useCase.registerRecipient(s.foreignOrganization().getId()))
                .isInstanceOf(AccessDeniedException.class);

        signInAs(s.worker());
        assertThatThrownBy(() -> useCase.registerRecipient(s.organization().getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("원문 열람은 관리자·담당자·보호자 모두 거부된다")
    void rawContentIsDeniedForEveryone() {
        for (OrganizationMember member : new OrganizationMember[]{s.admin(), s.worker(), s.guardian()}) {
            signInAs(member);
            assertThatThrownBy(useCase::readRawContent).isInstanceOf(AccessDeniedException.class);
        }
    }

    /**
     * SpEL이 {@code authentication}을 읽는 순간 예외가 나 표현식 평가 예외로 감싸진다. HTTP 요청은 필터 단에서
     * 먼저 401로 끝나므로 이 경로는 스케줄러 등 인증 없는 내부 호출에서만 탄다 — 어떤 경우든 실행되지 않는다.
     */
    @Test
    @DisplayName("인증 없이 호출하면 실행되지 않는다")
    void unauthenticatedCallIsRejected() {
        assertThatThrownBy(() -> useCase.readRecipient(s.recipient().getId()))
                .hasRootCauseInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("컨트롤러 안에서 메서드 보안이 거부하면 500이 아니라 403 공통 응답이 나간다")
    void deniedInsideControllerBecomesForbidden() throws Exception {
        MockMvc mockMvc = webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String workerToken = "Bearer " + tokenService.issueAccessToken(
                s.worker().getAccount().getAccountUuid(),
                s.organization().getPublicId(),
                s.worker().getAccount().getAuthVersion());

        mockMvc.perform(get(METHOD_SECURED, s.recipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(METHOD_SECURED, s.otherRecipient().getPublicId()).header(HttpHeaders.AUTHORIZATION, workerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4002));
    }

    /** 필터가 만드는 것과 같은 인증 객체를 SecurityContext에 넣는다. */
    private void signInAs(OrganizationMember member) {
        Authentication authentication = actorResolver.resolveActive(
                        member.getAccount().getAccountUuid(),
                        member.getOrganization().getPublicId(),
                        member.getAccount().getAuthVersion())
                .map(BackofficeAuthenticationFilter::toAuthentication)
                .orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
