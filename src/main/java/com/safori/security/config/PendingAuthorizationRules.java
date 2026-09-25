package com.safori.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 권한(인가) 규칙이 아직 붙지 않은 백오피스 API. 로그인한 백오피스 구성원이면 권한과 관계없이 통과한다.
 *
 * <p>API를 먼저 만들고 권한은 나중에 붙이기로 분업했다. 권한 담당자는 여기서 경로를 하나씩 빼서
 * 실제 권한 규칙({@link BackofficeRequestAuthorization#permission}, {@code recipient})으로 옮긴다.
 * 로그인은 요구한다 — 요청한 구성원의 기관을 토큰에서 알아야 하기 때문이다.
 */
@Configuration
public class PendingAuthorizationRules {

    @Bean
    BackofficeAuthorizationRules pendingAuthorization(BackofficeRequestAuthorization authz) {
        return registry -> registry
                // 대상자 조회·등록 (권한 담당자: RECIPIENT_CREATE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients/lookup"),
                        antMatcher(HttpMethod.POST, "/v1/api/admin/care-recipients"))
                .access(authz.member())
                // 담당자 등록·정보 수정 (권한 담당자: MEMBER_MANAGE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/admin/managers"),
                        antMatcher(HttpMethod.PUT, "/v1/api/admin/managers/{managerId}"))
                .access(authz.member())
                // 담당자 목록·상세 (권한 담당자: ASSIGNMENT_READ 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/managers"),
                        antMatcher(HttpMethod.GET, "/v1/api/admin/managers/{managerId}"))
                .access(authz.member())
                // 담당자 배정·변경·해제·일괄 변경 (권한 담당자: ASSIGNMENT_MANAGE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/managers/{managerId}/care-recipients"),
                        antMatcher("/v1/api/admin/care-recipients/{careRecipientId}/manager"))
                .access(authz.member())
                // 대상자 현황·목록·상세 (권한 담당자: RECIPIENT_READ 로 옮길 것. 목록 범위는 이미 권한 범위로 거르고,
                // 상세는 authz.recipient(RECIPIENT_READ, "careRecipientId")로 옮기면 담당자는 본인 배정만 열린다)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients"),
                        antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients/{careRecipientId}"))
                .access(authz.member())
                // 기록 상세·처리 상태 변경 (권한 담당자: CARE_REASON_READ / CARE_TASK_COMPLETE 로 옮길 것)
                .requestMatchers(antMatcher("/v1/api/admin/care-recipients/{careRecipientId}/records/{recordId}"))
                .access(authz.member())
                // 일지 폼·등록·상세 (권한 담당자: 폼은 member, 등록은 WORK_LOG_WRITE, 상세는 authz.recipient 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/journal-form"),
                        antMatcher("/v1/api/admin/care-recipients/{careRecipientId}/journals"),
                        antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients/{careRecipientId}/journals/{journalId}"))
                .access(authz.member())
                // 대상자 정보 수정 (권한 담당자: RECIPIENT_CREATE 로 옮길 것. 담당자 허용 여부는 기획 확인 필요)
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/care-recipients/{careRecipientId}"))
                .access(authz.member())
                // 보호자 등록·목록·상세·수정 (권한 담당자: MEMBER_MANAGE 로 옮길 것)
                .requestMatchers(antMatcher("/v1/api/admin/guardians"),
                        antMatcher("/v1/api/admin/guardians/{guardianId}"))
                .access(authz.member())
                // 보호자 연결·해제 (권한 담당자: GUARDIAN_LINK_MANAGE 로 옮길 것)
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/care-recipients/{careRecipientId}/guardians"),
                        antMatcher(HttpMethod.DELETE, "/v1/api/admin/care-recipients/{careRecipientId}/guardians/{guardianId}"))
                .access(authz.member())
                // [개발용] 기록 추가 — 운영에서는 컨트롤러가 없다
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/admin/dev/care-recipients/{careRecipientId}/records"))
                .access(authz.member());
    }
}
