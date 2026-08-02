package com.safori.common.consts;

import lombok.experimental.UtilityClass;

/**
 * 푸시 알림 문구·페이로드 키 상수.
 *
 * <p>{@code data} 페이로드 키/타입 값은 클라이언트 딥링크 라우팅과 합의된 스킴이다.
 * 값을 바꾸면 앱의 라우팅도 함께 바뀌어야 한다.
 */
@UtilityClass
public final class NotificationStaticValues {

    // ── 페이로드 공통 키 ─────────────────────────────────────────────
    /** 알림 종류 식별 키. 클라이언트는 이 값으로 이동할 화면을 결정한다. */
    public static final String DATA_TYPE = "type";

    // ── 주간 리포트 도착 ────────────────────────────────────────────
    public static final String TYPE_WEEKLY_REPORT = "WEEKLY_REPORT";
    public static final String KEY_YEAR_MONTH = "yearMonth";
    public static final String KEY_WEEK = "week";
    public static final String WEEKLY_REPORT_TITLE = "이번 주 감정 리포트가 도착했어요";
    public static final String WEEKLY_REPORT_BODY = "지난 한 주 마음이 어떠셨는지 도란이가 정리해 두었어요. 확인해 보실래요?";

    // ── 마음일기 상담 제안 ──────────────────────────────────────────
    public static final String TYPE_MIND_DIARY_OFFER = "MIND_DIARY_OFFER";
    public static final String KEY_OFFER_ID = "offerId";
    public static final String MIND_DIARY_OFFER_TITLE = "도란이가 이야기를 나누고 싶어 해요";
    public static final String MIND_DIARY_OFFER_BODY = "요즘 마음이 어떠신지 도란이와 잠깐 도란도란 이야기 나눠보실래요?";
}
