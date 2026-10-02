package com.safori.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_TASK_COMPLETE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_LINK_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.MEMBER_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_CREATE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_WRITE;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * 백오피스 API({@code /v1/api/admin/**}) 권한 규칙. 노션 API 표의 권한 열을 따르며, 한 API에 적힌 권한은 모두 필요하다.
 * 먼저 등록한 규칙이 우선한다(구체적인 경로를 위에 둔다).
 *
 * <ul>
 *   <li>{@code permission}/{@code allOf}: 권한만 본다. 기관 단위 기능이나, 목록처럼 서비스가 권한 범위로 거르는 경로.</li>
 *   <li>{@code recipient}: 경로의 대상자가 각 권한 범위(관리자 기관 전체 / 담당자 현재 배정)에 있어야 한다.</li>
 * </ul>
 * 규칙 밖에서 서비스가 더 거르는 것: 처리 상태 변경·일지 작성은 현재 담당자만(4461).
 */
@Configuration
public class AdminApiAuthorizationRules {

    private static final String RECIPIENT = "/v1/api/admin/care-recipients/{careRecipientId}";
    private static final String ID = "careRecipientId";

    @Bean
    BackofficeAuthorizationRules adminApiAuthorization(BackofficeRequestAuthorization authz) {
        return registry -> registry
                // 대상자 추가 전 조회·등록. lookup은 상세({careRecipientId})보다 먼저 둔다
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients/lookup"),
                        antMatcher(HttpMethod.POST, "/v1/api/admin/care-recipients"))
                .access(authz.permission(RECIPIENT_CREATE))
                // 대상자 정보 수정 (노션의 RECIPIENT_UPDATE 는 아직 없는 권한이라 RECIPIENT_CREATE 로 대신한다).
                // 비활성 대상자를 다시 활성화해야 하므로 대상자 범위(활성 대상자만) 대신 권한만 본다 — 기관은 서비스가 확인
                .requestMatchers(antMatcher(HttpMethod.PUT, RECIPIENT))
                .access(authz.allOf(RECIPIENT_CREATE, RECIPIENT_READ, CARE_STATUS_READ, CARE_REASON_READ,
                        RECIPIENT_GUARDIAN_READ, WORK_LOG_READ, WORK_LOG_HISTORY_READ))
                // 대상자 리스트 (범위는 서비스가 권한 범위로 거른다) / 상세
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/care-recipients"))
                .access(authz.allOf(RECIPIENT_READ, CARE_STATUS_READ, CARE_REASON_READ))
                .requestMatchers(antMatcher(HttpMethod.GET, RECIPIENT))
                .access(authz.recipient(ID, RECIPIENT_READ, CARE_STATUS_READ, CARE_REASON_READ,
                        RECIPIENT_GUARDIAN_READ, WORK_LOG_READ, WORK_LOG_HISTORY_READ))
                // 담당자 배정·변경·해제
                .requestMatchers(antMatcher(RECIPIENT + "/manager"))
                .access(authz.permission(ASSIGNMENT_MANAGE))
                // 기록 상세 / 처리 상태 변경
                .requestMatchers(antMatcher(HttpMethod.GET, RECIPIENT + "/records/{recordId}"))
                .access(authz.recipient(ID, CARE_STATUS_READ, CARE_REASON_READ))
                .requestMatchers(antMatcher(HttpMethod.PATCH, RECIPIENT + "/records/{recordId}"))
                .access(authz.recipient(ID, CARE_TASK_COMPLETE, CARE_STATUS_READ, CARE_REASON_READ))
                // 일지 등록 / 상세 / 보호자 공개 변경(노션 표에 없음 — 등록과 같은 권한)
                .requestMatchers(antMatcher(HttpMethod.POST, RECIPIENT + "/journals"))
                .access(authz.recipient(WORK_LOG_WRITE, ID))
                .requestMatchers(antMatcher(HttpMethod.GET, RECIPIENT + "/journals/{journalId}"))
                .access(authz.recipient(ID, WORK_LOG_READ, CARE_REASON_READ, WORK_LOG_HISTORY_READ))
                .requestMatchers(antMatcher(HttpMethod.PATCH, RECIPIENT + "/journals/{journalId}"))
                .access(authz.recipient(WORK_LOG_WRITE, ID))
                // 보호자 연결·해제
                .requestMatchers(antMatcher(HttpMethod.PUT, RECIPIENT + "/guardians"),
                        antMatcher(HttpMethod.DELETE, RECIPIENT + "/guardians/{guardianId}"))
                .access(authz.allOf(GUARDIAN_LINK_MANAGE, GUARDIAN_READ, RECIPIENT_READ))
                // 일지 폼 / 일지 목록 (범위는 서비스가 권한 범위로 거른다)
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/journal-form"))
                .access(authz.permission(WORK_LOG_WRITE))
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/journals"))
                .access(authz.allOf(WORK_LOG_READ, CARE_REASON_READ, WORK_LOG_HISTORY_READ))
                // 담당자 등록·정보 수정 / 목록 / 상세 / 배정 인원 일괄 변경
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/admin/managers"),
                        antMatcher(HttpMethod.PUT, "/v1/api/admin/managers/{managerId}"))
                .access(authz.permission(MEMBER_MANAGE))
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/managers"))
                .access(authz.permission(ASSIGNMENT_READ))
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/managers/{managerId}"))
                .access(authz.allOf(ASSIGNMENT_READ, RECIPIENT_READ))
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/managers/{managerId}/care-recipients"))
                .access(authz.permission(ASSIGNMENT_MANAGE))
                // 보호자 등록 / 정보 수정 / 목록·상세 (기관 전체 보호자 명부는 관리자만)
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/admin/guardians"))
                .access(authz.permission(MEMBER_MANAGE))
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/guardians/{guardianId}"))
                .access(authz.allOf(MEMBER_MANAGE, GUARDIAN_READ, RECIPIENT_READ))
                .requestMatchers(antMatcher(HttpMethod.GET, "/v1/api/admin/guardians"),
                        antMatcher(HttpMethod.GET, "/v1/api/admin/guardians/{guardianId}"))
                .access(authz.allOf(GUARDIAN_READ, RECIPIENT_READ))
                // 내 정보 수정 — 본인 정보라 로그인만 (항목별 제한은 서비스가 검사)
                .requestMatchers(antMatcher(HttpMethod.PUT, "/v1/api/admin/me"))
                .access(authz.member())
                // 푸시 알림 기기 등록·삭제 — 본인 기기라 로그인만
                .requestMatchers(antMatcher("/v1/api/admin/device-tokens"))
                .access(authz.member())
                // [개발용] 기록 추가 — 운영에서는 컨트롤러가 없다
                .requestMatchers(antMatcher(HttpMethod.POST, "/v1/api/admin/dev/care-recipients/{careRecipientId}/records"))
                .access(authz.member());
    }
}
