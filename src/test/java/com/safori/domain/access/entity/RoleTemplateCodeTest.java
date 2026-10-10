package com.safori.domain.access.entity;

import com.safori.domain.access.PhotoPermissionMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static com.safori.domain.access.entity.PermissionCode.CARE_REASON_READ;
import static com.safori.domain.access.entity.PermissionCode.CARE_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.GUARDIAN_STATUS_READ;
import static com.safori.domain.access.entity.PermissionCode.RAW_CONTENT_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_GUARDIAN_READ;
import static com.safori.domain.access.entity.PermissionCode.RECIPIENT_UPDATE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_APPROVE;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_HISTORY_READ;
import static com.safori.domain.access.entity.PermissionCode.WORK_LOG_READ;
import static com.safori.domain.access.entity.RoleTemplateCode.CARE_WORKER;
import static com.safori.domain.access.entity.RoleTemplateCode.GUARDIAN;
import static com.safori.domain.access.entity.RoleTemplateCode.ORG_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코드 카탈로그(기본 역할 구성)가 권한표와 같은지 검증한다.
 */
class RoleTemplateCodeTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(RoleTemplateCode.class)
    @DisplayName("기본 역할의 권한 구성과 데이터 범위가 권한표와 같다")
    void templateMatchesPhoto(RoleTemplateCode role) {
        assertThat(role.getDefaultPermissions())
                .containsExactlyInAnyOrderElementsOf(PhotoPermissionMatrix.allowedPermissions(role));
        assertThat(role.getDefaultPermissions())
                .allSatisfy(permission -> assertThat(PhotoPermissionMatrix.expectedScope(role, permission))
                        .contains(role.getDataScope()));
    }

    @Test
    @DisplayName("원문 열람은 어떤 기본 역할에도 없고, 기관이 부여할 수도 없다")
    void rawContentIsForbiddenForEveryRole() {
        assertThat(RoleTemplateCode.values())
                .noneMatch(role -> role.getDefaultPermissions().contains(RAW_CONTENT_READ));
        assertThat(RAW_CONTENT_READ.isOrganizationAssignable()).isFalse();
    }

    @Test
    @DisplayName("기본 역할에는 기관이 부여할 수 없는 권한이 들어가지 않는다")
    void templatesContainOnlyAssignablePermissions() {
        assertThat(Arrays.stream(RoleTemplateCode.values())
                .flatMap(role -> role.getDefaultPermissions().stream()))
                .allMatch(PermissionCode::isOrganizationAssignable);
    }

    @Test
    @DisplayName("보호자는 내부 상태·확인 사유 대신 공개 전용 상태 조회만 갖는다")
    void guardianReadsPublishedStatusOnly() {
        assertThat(GUARDIAN.getDefaultPermissions())
                .contains(GUARDIAN_STATUS_READ)
                .doesNotContain(CARE_STATUS_READ, CARE_REASON_READ, GUARDIAN_READ,
                        RECIPIENT_GUARDIAN_READ, WORK_LOG_READ, WORK_LOG_HISTORY_READ);
    }

    @Test
    @DisplayName("기관 전체 보호자 목록·상세 조회는 관리자에게만 부여한다")
    void guardianDirectoryIsAdminOnly() {
        assertThat(ORG_ADMIN.getDefaultPermissions()).contains(GUARDIAN_READ);
        assertThat(CARE_WORKER.getDefaultPermissions()).doesNotContain(GUARDIAN_READ);
    }

    @Test
    @DisplayName("대상자 수정 권한은 기본 기관 관리자에게만 부여한다")
    void recipientUpdateIsAdminOnly() {
        assertThat(ORG_ADMIN.getDefaultPermissions()).contains(RECIPIENT_UPDATE);
        assertThat(CARE_WORKER.getDefaultPermissions()).doesNotContain(RECIPIENT_UPDATE);
        assertThat(GUARDIAN.getDefaultPermissions()).doesNotContain(RECIPIENT_UPDATE);
    }

    @Test
    @DisplayName("담당자는 배정 대상의 연결 보호자와 업무일지를 조회할 수 있다")
    void workerReadsAssignedRecipientGuardiansAndWorkLogs() {
        assertThat(CARE_WORKER.getDefaultPermissions())
                .contains(RECIPIENT_GUARDIAN_READ, WORK_LOG_READ, WORK_LOG_HISTORY_READ)
                .doesNotContain(WORK_LOG_HISTORY_APPROVE);
    }

    @Test
    @DisplayName("이전 업무일지 열람 승인은 관리자에게만 부여한다")
    void workLogHistoryApprovalIsAdminOnly() {
        assertThat(ORG_ADMIN.getDefaultPermissions())
                .contains(WORK_LOG_HISTORY_READ, WORK_LOG_HISTORY_APPROVE);
        assertThat(CARE_WORKER.getDefaultPermissions()).doesNotContain(WORK_LOG_HISTORY_APPROVE);
    }
}
