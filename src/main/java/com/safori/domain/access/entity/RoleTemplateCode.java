package com.safori.domain.access.entity;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.ASSIGNMENT_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_TASK_COMPLETE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_LINK_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.MEMBER_MANAGE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_CREATE;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_UPDATE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_APPROVE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_READ;
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
 * | 보호자 목록·상세                   | 소속 기관     | 금지          | 금지              |
 * | 대상자에 연결된 보호자 정보          | 소속 기관     | 본인 배정     | 금지              |
 * | 업무일지 조회                      | 소속 기관     | 현재 배정 기간 | 금지              |
 * | 이전 담당자의 업무일지              | 소속 기관     | 별도 승인 필요 | 금지              |
 * | 업무일지 작성·업무 완료             | 소속 기관     | 현재 본인 배정 | 금지              |
 * | 대상자 등록·담당자 배정/변경/해제    | 소속 기관     | 금지          | 금지              |
 * | 보호자 연결·해제                   | 소속 기관     | 금지          | 금지              |
 * | 어르신 일기·녹음·상담 원문·다운로드  | 금지          | 금지          | 금지              |
 * </pre>
 *
 * <p>기관 생성 시 템플릿마다 기관 소유 역할과 같은 이름의 기본 그룹을 만든다. 이후 기관은 자기 역할의
 * 권한을 바꿀 수 있다.
 *
 * <p>기본 권한은 버전별로 추가된 것만 {@link Revision}으로 쌓는다. 현재 기본 권한은 모든 revision의 합이고,
 * 현재 버전은 마지막 revision 번호다. 기본 권한을 늘릴 때는 기존 revision을 고치지 말고 다음 번호의 revision을
 * 덧붙인다. 기동 시 옛 버전으로 만들어진 기관 역할에는 그 뒤 revision에서 추가된 권한만 넣으므로
 * ({@code AccessProvisioningDomainService#upgradeTemplateRoles}), 기관이 직접 뺀 권한은 되살아나지 않는다.
 * revision은 추가만 표현한다. 기본 권한을 빼야 하면 기존 기관 역할에서 자동으로 빼지 않도록 별도로 설계한다.
 */
@Getter
public enum RoleTemplateCode {

    ORG_ADMIN("기관 관리자", DataScope.ORGANIZATION, List.of(
            revision(1,
                    RECIPIENT_READ,
                    ASSIGNMENT_READ,
                    CARE_STATUS_READ,
                    CARE_REASON_READ,
                    WORK_LOG_WRITE,
                    CARE_TASK_COMPLETE,
                    RECIPIENT_CREATE,
                    ASSIGNMENT_MANAGE,
                    GUARDIAN_LINK_MANAGE,
                    MEMBER_MANAGE),
            revision(2,
                    GUARDIAN_READ,
                    RECIPIENT_GUARDIAN_READ,
                    WORK_LOG_READ,
                    WORK_LOG_HISTORY_READ,
                    WORK_LOG_HISTORY_APPROVE),
            revision(3,
                    RECIPIENT_UPDATE))),

    CARE_WORKER("담당자", DataScope.ASSIGNED_RECIPIENT, List.of(
            revision(1,
                    RECIPIENT_READ,
                    CARE_STATUS_READ,
                    CARE_REASON_READ,
                    WORK_LOG_WRITE,
                    CARE_TASK_COMPLETE),
            revision(2,
                    RECIPIENT_GUARDIAN_READ,
                    WORK_LOG_READ,
                    WORK_LOG_HISTORY_READ))),

    GUARDIAN("보호자", DataScope.LINKED_RECIPIENT, List.of(
            revision(1,
                    RECIPIENT_READ,
                    GUARDIAN_STATUS_READ)));

    private final String displayName;
    private final long version;
    private final DataScope dataScope;
    private final List<Revision> revisions;
    private final Set<PermissionCode> defaultPermissions;

    RoleTemplateCode(String displayName, DataScope dataScope, List<Revision> revisions) {
        EnumSet<PermissionCode> permissions = EnumSet.noneOf(PermissionCode.class);
        for (int i = 0; i < revisions.size(); i++) {
            Revision revision = revisions.get(i);
            if (revision.version() != i + 1) {
                throw new IllegalArgumentException(name() + " revision은 1부터 빠짐없이 이어져야 한다: " + revision.version());
            }
            for (PermissionCode permission : revision.added()) {
                if (!permissions.add(permission)) {
                    throw new IllegalArgumentException(name() + " revision " + revision.version()
                            + "이 이미 있는 권한을 다시 추가한다: " + permission);
                }
            }
        }
        this.displayName = displayName;
        this.version = revisions.size();
        this.dataScope = dataScope;
        this.revisions = List.copyOf(revisions);
        this.defaultPermissions = Collections.unmodifiableSet(permissions);
    }

    /** 주어진 버전 뒤의 revision에서 추가된 기본 권한. 현재 버전 이상이면 비어 있다. */
    public Set<PermissionCode> permissionsAddedAfter(long version) {
        EnumSet<PermissionCode> added = EnumSet.noneOf(PermissionCode.class);
        revisions.stream()
                .filter(revision -> revision.version() > version)
                .forEach(revision -> added.addAll(revision.added()));
        return Collections.unmodifiableSet(added);
    }

    private static Revision revision(long version, PermissionCode first, PermissionCode... rest) {
        return new Revision(version, Collections.unmodifiableSet(EnumSet.of(first, rest)));
    }

    /** 한 버전에서 기본 권한에 추가된 것. */
    public record Revision(long version, Set<PermissionCode> added) {
    }
}
