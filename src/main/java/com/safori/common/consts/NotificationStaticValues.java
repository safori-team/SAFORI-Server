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

    // ── 마음일기 감정 분석 완료 ─────────────────────────────────────
    public static final String TYPE_DIARY_ANALYSIS_DONE = "DIARY_ANALYSIS_DONE";
    public static final String KEY_VOICE_ID = "voiceId";
    public static final String DIARY_ANALYSIS_DONE_TITLE = "마음일기 분석이 끝났어요";
    public static final String DIARY_ANALYSIS_DONE_BODY = "오늘 마음일기 감정 분석이 준비됐어요. 도란이의 이야기를 확인해 보실래요?";

    // ── 도란이 응답 확정 ────────────────────────────────────────────
    // 성공·실패를 한 타입으로 합치지 않는다 — 탭했을 때 앱이 보여줄 화면 상태가 다르다.
    // 발송 게이트가 없어 매 턴 나가므로, 앱은 포그라운드 수신 시 표시를 억제해야 한다.
    public static final String TYPE_CHAT_REPLY_DONE = "CHAT_REPLY_DONE";
    public static final String TYPE_CHAT_REPLY_FAILED = "CHAT_REPLY_FAILED";
    public static final String KEY_SESSION_ID = "sessionId";
    public static final String KEY_MESSAGE_ID = "messageId";
    public static final String CHAT_REPLY_DONE_TITLE = "도란이가 답변을 보냈어요";
    public static final String CHAT_REPLY_DONE_BODY = "도란이의 이야기가 준비됐어요. 확인해 보실래요?";
    public static final String CHAT_REPLY_FAILED_TITLE = "답변을 만들지 못했어요";
    public static final String CHAT_REPLY_FAILED_BODY = "도란이가 잠시 생각이 꼬였나 봐요. 다시 시도해 보실래요?";
}
