package com.safori.domain.access.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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

/**
 * SAFORI가 기관에 제공하는 기본 역할 구성. 권한표를 그대로 옮긴 것이다.
 *
 * <pre>
 * | 기능                              | 기관 관리자   | 담당자        | 보호자             |
 * |-----------------------------------|--------------|--------------|-------------------|
 * | 어르신 조회                        | 소속 기관 전체 | 현재 본인 배정 | 현재 연결          |
 * | 담당자별 배정 대상 조회             | 소속 기관     | 금지          | 금지              |
 * | 상태·조치 조회                     | 소속 기관     | 본인 배정     | 연결 대상 공개 항목 |
 * | 확인 사유·자동 분석 근거            | 소속 기관     | 본인 배정     | 금지              |
 * | 업무일지 작성·업무 완료             | 소속 기관     | 현재 본인 배정 | 금지              |
 * | 대상자 등록·담당자 배정/변경/해제    | 소속 기관     | 금지          | 금지              |
 * | 보호자 연결·해제                   | 소속 기관     | 금지          | 금지              |
 * | 어르신 일기·녹음·상담 원문·다운로드  | 금지          | 금지          | 금지              |
 * </pre>
 *
 * <p>기관 생성 시 템플릿마다 기관 소유 역할과 같은 이름의 기본 그룹을 만든다. 이후 기관은 자기 역할의
 * 권한을 바꿀 수 있고, 템플릿이 바뀌어도 기존 기관 역할을 자동으로 덮어쓰지 않는다. 기본 권한 구성을
 * 바꿀 때는 {@link #version}을 올린다.
 */
@Getter
public enum RoleTemplateCode {

    ORG_ADMIN("기관 관리자", 1, DataScope.ORGANIZATION, EnumSet.of(
            RECIPIENT_READ,
            ASSIGNMENT_READ,
            CARE_STATUS_READ,
            CARE_REASON_READ,
            WORK_LOG_WRITE,
            CARE_TASK_COMPLETE,
            RECIPIENT_CREATE,
            ASSIGNMENT_MANAGE,
            GUARDIAN_LINK_MANAGE,
            MEMBER_MANAGE)),

    CARE_WORKER("담당자", 1, DataScope.ASSIGNED_RECIPIENT, EnumSet.of(
            RECIPIENT_READ,
            CARE_STATUS_READ,
            CARE_REASON_READ,
            WORK_LOG_WRITE,
            CARE_TASK_COMPLETE)),

    GUARDIAN("보호자", 1, DataScope.LINKED_RECIPIENT, EnumSet.of(
            RECIPIENT_READ,
            GUARDIAN_STATUS_READ));

    private final String displayName;
    private final long version;
    private final DataScope dataScope;
    private final Set<PermissionCode> defaultPermissions;

    RoleTemplateCode(String displayName, long version, DataScope dataScope, EnumSet<PermissionCode> defaultPermissions) {
        this.displayName = displayName;
        this.version = version;
        this.dataScope = dataScope;
        this.defaultPermissions = Collections.unmodifiableSet(defaultPermissions);
    }
}
