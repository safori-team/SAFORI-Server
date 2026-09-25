package com.safori.domain.access.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 백오피스 API가 검사하는 최소 행동 권한 카탈로그.
 *
 * <p>DB {@code access_permission}은 이 enum을 코드 기준으로 멱등 동기화한 사본이다
 * ({@code AccessProvisioningDomainService#synchronizeCatalog}). 권한을 추가하거나 설명을 바꿀 때는
 * 이 enum만 고친다. DB에는 enum에 없는 코드가 남아 있어도 권한 계산에서 무시된다.
 *
 * <p>각 권한이 권한표의 어느 기능에 해당하는지는 상수 주석에 적었다. 누구의 데이터에
 * 대해서인가(데이터 범위)는 권한이 아니라 역할의 {@link DataScope}가 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum PermissionCode {

    /** 어르신 조회. */
    RECIPIENT_READ("어르신 조회", true),

    /** 담당자별 배정 대상 조회. */
    ASSIGNMENT_READ("담당자별 배정 대상 조회", true),

    /** 상태·조치 조회 (관리자·담당자). */
    CARE_STATUS_READ("상태·조치 조회", true),

    /** 상태·조치 조회 (보호자). 보호자 공개가 허용된 항목만 담는 별도 조회다. */
    GUARDIAN_STATUS_READ("보호자 공개 상태·조치 조회", true),

    /** 확인 사유·자동 분석 근거. 원문 인용 없이 구조화된 근거만 허용한다. */
    CARE_REASON_READ("확인 사유·자동 분석 근거 조회", true),

    /** 업무일지 작성. */
    WORK_LOG_WRITE("업무일지 작성", true),

    /** 업무 완료. */
    CARE_TASK_COMPLETE("업무 완료", true),

    /** 대상자 등록. */
    RECIPIENT_CREATE("대상자 등록", true),

    /** 담당자 배정·변경·해제. */
    ASSIGNMENT_MANAGE("담당자 배정·변경·해제", true),

    /** 보호자 연결·해제. */
    GUARDIAN_LINK_MANAGE("보호자 연결·해제", true),

    /** 기관 초대·승인·정지 등 구성원 관리. 권한표 밖이지만 "기관 초대·승인으로 가입" 결정에 필요하다. */
    MEMBER_MANAGE("구성원 초대·승인·관리", true),

    /**
     * 어르신 일기·녹음·상담 원문 및 다운로드 URL. 모든 역할에 금지다.
     * 기본 역할에 없을 뿐 아니라 기관이 자기 역할에 추가할 수도 없다.
     */
    RAW_CONTENT_READ("어르신 일기·녹음·상담 원문 및 다운로드 URL", false);

    private final String description;

    /** false면 기관이 자기 역할에 이 권한을 넣을 수 없다. */
    private final boolean organizationAssignable;

    public static Optional<PermissionCode> find(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.name().equals(code))
                .findFirst();
    }
}
