package com.safori.security;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.domain.care.entity.CareRecipient;
import com.safori.domain.care.repository.CareRecipientRepository;
import com.safori.security.config.BackofficeAuthorizationRules;
import com.safori.security.config.BackofficeRequestAuthorization;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 실제 API 없이 필터 단·메서드 단 인가를 검증하기 위한 테스트 전용 엔드포인트와 규칙.
 * API 담당자가 규칙 빈과 UseCase에 권한을 거는 방식의 예시이기도 하다.
 */
@TestConfiguration
@Import(BackofficeFixture.class)
public class BackofficeSecurityTestConfig {

    public static final String ASSIGNMENTS = "/v1/api/backoffice/test/assignments";
    public static final String RECIPIENT = "/v1/api/backoffice/test/recipients/{recipientId}";
    public static final String GUARDIAN_STATUS = "/v1/api/guardian/test/recipients/{recipientId}/status";
    public static final String METHOD_SECURED = "/v1/api/backoffice/test/method-secured/{recipientId}";
    public static final String UNMAPPED = "/v1/api/backoffice/test/unmapped";

    @Bean
    BackofficeAuthorizationRules testBackofficeRules(BackofficeRequestAuthorization authz) {
        return registry -> registry
                .requestMatchers(antMatcher(HttpMethod.GET, ASSIGNMENTS))
                .access(authz.permission(ASSIGNMENT_READ))
                .requestMatchers(antMatcher(HttpMethod.GET, RECIPIENT))
                .access(authz.recipient(RECIPIENT_READ, "recipientId"))
                .requestMatchers(antMatcher(HttpMethod.GET, GUARDIAN_STATUS))
                .access(authz.recipient(GUARDIAN_STATUS_READ, "recipientId"))
                .requestMatchers(antMatcher(HttpMethod.GET, METHOD_SECURED))
                .access(authz.member());
    }

    @Bean
    SampleRecipientUseCase sampleRecipientUseCase() {
        return new SampleRecipientUseCase();
    }

    /** UseCase에 메서드 보안을 거는 예시. Spring 프록시를 거쳐 호출돼야 검사된다. */
    public static class SampleRecipientUseCase {

        @PreAuthorize("@backofficeAccessPolicy.canAccessRecipient(authentication, 'RECIPIENT_READ', #recipientId)")
        public String readRecipient(Long recipientId) {
            return "recipient:" + recipientId;
        }

        @PreAuthorize("@backofficeAccessPolicy.canAccessOrganization(authentication, 'RECIPIENT_CREATE', #organizationId)")
        public String registerRecipient(Long organizationId) {
            return "registered";
        }

        @PreAuthorize("@backofficeAccessPolicy.hasPermission(authentication, 'RAW_CONTENT_READ')")
        public String readRawContent() {
            return "raw";
        }
    }

    /** 설정 클래스의 멤버 클래스라 이 설정을 import한 테스트 컨텍스트에만 등록된다. */
    @RestController
    public static class TestBackofficeController {

        private final SampleRecipientUseCase useCase;
        private final CareRecipientRepository recipientRepository;

        public TestBackofficeController(SampleRecipientUseCase useCase, CareRecipientRepository recipientRepository) {
            this.useCase = useCase;
            this.recipientRepository = recipientRepository;
        }

        @GetMapping(ASSIGNMENTS)
        public ApiResponseDto<String> assignments() {
            return ApiResponseDto.onSuccess("assignments");
        }

        @GetMapping(RECIPIENT)
        public ApiResponseDto<String> recipient(@PathVariable("recipientId") String recipientId) {
            return ApiResponseDto.onSuccess(recipientId);
        }

        @GetMapping(GUARDIAN_STATUS)
        public ApiResponseDto<String> guardianStatus(@PathVariable("recipientId") String recipientId) {
            return ApiResponseDto.onSuccess(recipientId);
        }

        @GetMapping(UNMAPPED)
        public ApiResponseDto<String> unmapped() {
            return ApiResponseDto.onSuccess("unmapped");
        }

        /** 필터는 구성원이면 통과시키고, 어르신 범위 판정은 UseCase의 @PreAuthorize가 한다. */
        @GetMapping(METHOD_SECURED)
        public ApiResponseDto<String> methodSecured(@PathVariable("recipientId") String recipientId) {
            Long id = recipientRepository.findByPublicId(recipientId)
                    .map(CareRecipient::getId)
                    .orElseThrow();
            return ApiResponseDto.onSuccess(useCase.readRecipient(id));
        }
    }
}
