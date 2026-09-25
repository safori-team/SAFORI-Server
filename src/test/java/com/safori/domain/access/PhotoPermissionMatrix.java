package com.safori.domain.access;

import com.safori.domain.access.entity.DataScope;
import com.safori.domain.access.entity.PermissionCode;
import com.safori.domain.access.entity.RoleTemplateCode;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.safori.domain.access.entity.DataScope.ASSIGNED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.LINKED_RECIPIENT;
import static com.safori.domain.access.entity.DataScope.ORGANIZATION;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_TASK_COMPLETE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_LINK_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.MEMBER_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_CREATE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_WRITE;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.GUARDIAN;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static java.util.Map.entry;

/**
 * 권한표를 (역할, 권한) → 허용 범위로 옮긴 테스트 기대값. 여기 없는 조합은 금지다.
 * 구현({@link RoleTemplateCode})과 따로 적어 두고 두 쪽이 같은지 검증한다.
 *
 * <pre>
 * | 기능                              | 기관 관리자    | 담당자        | 보호자             |
 * |-----------------------------------|---------------|--------------|-------------------|
 * | 어르신 조회                        | 소속 기관 전체 | 현재 본인 배정 | 현재 연결          |
 * | 담당자별 배정 대상 조회             | 소속 기관만    | 금지          | 금지              |
 * | 상태·조치 조회                     | 소속 기관      | 본인 배정     | 연결 대상 공개 항목 |
 * | 확인 사유·자동 분석 근거            | 소속 기관      | 본인 배정     | 금지              |
 * | 업무일지 작성·업무 완료             | 소속 기관      | 현재 본인 배정 | 금지              |
 * | 대상자 등록·담당자 배정/변경/해제    | 소속 기관      | 금지          | 금지              |
 * | 보호자 연결·해제                   | 소속 기관      | 금지          | 금지              |
 * | 어르신 일기·녹음·상담 원문·다운로드  | 금지          | 금지          | 금지              |
 * </pre>
 * 권한표 밖으로 구성원 초대·승인(MEMBER_MANAGE)은 기관 관리자만 갖는다.
 */
public final class PhotoPermissionMatrix {

    private static final Map<RoleTemplateCode, Map<PermissionCode, DataScope>> EXPECTED = Map.of(
            ORG_ADMIN, Map.ofEntries(
                    entry(RECIPIENT_READ, ORGANIZATION),        // 어르신 조회
                    entry(ASSIGNMENT_READ, ORGANIZATION),       // 담당자별 배정 대상 조회
                    entry(CARE_STATUS_READ, ORGANIZATION),      // 상태·조치 조회
                    entry(CARE_REASON_READ, ORGANIZATION),      // 확인 사유·자동 분석 근거
                    entry(WORK_LOG_WRITE, ORGANIZATION),        // 업무일지 작성
                    entry(CARE_TASK_COMPLETE, ORGANIZATION),    // 업무 완료
                    entry(RECIPIENT_CREATE, ORGANIZATION),      // 대상자 등록
                    entry(ASSIGNMENT_MANAGE, ORGANIZATION),     // 담당자 배정/변경/해제
                    entry(GUARDIAN_LINK_MANAGE, ORGANIZATION),  // 보호자 연결·해제
                    entry(MEMBER_MANAGE, ORGANIZATION)),        // (권한표 밖) 구성원 초대·승인
            CARE_WORKER, Map.ofEntries(
                    entry(RECIPIENT_READ, ASSIGNED_RECIPIENT),
                    entry(CARE_STATUS_READ, ASSIGNED_RECIPIENT),
                    entry(CARE_REASON_READ, ASSIGNED_RECIPIENT),
                    entry(WORK_LOG_WRITE, ASSIGNED_RECIPIENT),
                    entry(CARE_TASK_COMPLETE, ASSIGNED_RECIPIENT)),
            GUARDIAN, Map.ofEntries(
                    entry(RECIPIENT_READ, LINKED_RECIPIENT),
                    entry(GUARDIAN_STATUS_READ, LINKED_RECIPIENT)));

    private PhotoPermissionMatrix() {
    }

    public static Optional<DataScope> expectedScope(RoleTemplateCode role, PermissionCode permission) {
        return Optional.ofNullable(EXPECTED.get(role).get(permission));
    }

    public static Set<PermissionCode> allowedPermissions(RoleTemplateCode role) {
        return EXPECTED.get(role).keySet();
    }

    /** 모든 (역할, 권한) 조합과 기대값 설명. 파라미터 테스트의 표시 이름에 쓴다. */
    public static Stream<Arguments> cells() {
        return Arrays.stream(RoleTemplateCode.values())
                .flatMap(role -> Arrays.stream(PermissionCode.values())
                        .map(permission -> Arguments.of(role, permission, describe(role, permission))));
    }

    private static String describe(RoleTemplateCode role, PermissionCode permission) {
        return expectedScope(role, permission)
                .map(scope -> switch (scope) {
                    case ORGANIZATION -> "소속 기관 전체";
                    case ASSIGNED_RECIPIENT -> "현재 본인 배정";
                    case LINKED_RECIPIENT -> "현재 본인 연결";
                })
                .orElse("금지");
    }
}
