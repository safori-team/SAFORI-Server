package com.safori.domain.access.entity;

/**
 * 역할로 받은 권한이 미치는 어르신 범위.
 *
 * <p>권한({@link PermissionCode})은 "무엇을 할 수 있는가"만 말한다. 같은 {@code RECIPIENT_READ}라도
 * 관리자는 소속 기관 전체, 담당자는 본인 배정 어르신, 보호자는 본인과 연결된 어르신만 볼 수 있다.
 * 이 차이를 역할 이름 비교 없이 판정하려고 범위를 역할에 둔다.
 *
 * <p>어떤 범위든 본인 기관 밖으로 넘어가지 않는다.
 */
public enum DataScope {

    /** 소속 기관의 모든 어르신. */
    ORGANIZATION,

    /** 현재 본인에게 배정된 어르신 ({@code care_assignment.ended_at IS NULL}). */
    ASSIGNED_RECIPIENT,

    /** 현재 본인과 연결된 어르신 ({@code guardian_recipient_link.ended_at IS NULL}). */
    LINKED_RECIPIENT
}
