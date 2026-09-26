package com.safori.domain.care.entity;

/**
 * 확인 사유 종류(단계별 확인 방식 표). 사유마다 등급·제목·안내 문구가 고정이고, 설명 문장(숫자·감정)만 기록에 저장한다.
 */
public enum CareReasonType {

    /** 최근 30일 최신 상담 3회 중 2회 이상에서 기본 상담 후 '조금 더 이야기하기'를 선택. */
    COUNSEL_EXTENSION_REPEAT(CareStatusCode.INTEREST, "추가 상담 반복 이용",
            "다음 연락이나 방문 시 최근 마음에 걸리는 일이나 계속 생각나는 일이 있었는지 자연스럽게 살펴봐 주세요.", false),

    /** 최근 30일 최신 일기 3건 중 동일한 부정적 감정 대분류(슬픔/불안/분노)가 2건 이상. */
    SAME_EMOTION_REPEAT(CareStatusCode.CAUTION, "동일 감정 반복",
            "다음 연락이나 방문 시 최근 기분에 달라진 점이 있는지 살펴봐 주세요.", false),

    /**
     * 작성 주기 감소. 판정하지 않는다(개인별 작성 빈도 차이로 일괄 기준을 두기 어려워 실증 후 재검토).
     * 판정하던 동안 저장된 기록을 읽기 위해 값만 남긴다.
     */
    DIARY_INTERVAL_INCREASE(CareStatusCode.CAUTION, "작성 주기 감소",
            "다음 연락이나 방문 시 최근 마음일기 작성이 줄어든 이유와 안부를 함께 살펴봐 주세요.", false),

    /** SOS·담당자 연결 요청 또는 담당자 직접 등록. 요청할 때마다 새로 올린다. */
    HELP_REQUEST(CareStatusCode.URGENT, "도움 요청",
            "현재 상태를 바로 확인해 주세요.", true);

    private final CareStatusCode statusCode;
    private final String title;
    private final String guidance;
    private final boolean repeatable;

    CareReasonType(CareStatusCode statusCode, String title, String guidance, boolean repeatable) {
        this.statusCode = statusCode;
        this.title = title;
        this.guidance = guidance;
        this.repeatable = repeatable;
    }

    public CareStatusCode statusCode() {
        return statusCode;
    }

    public String title() {
        return title;
    }

    public String guidance() {
        return guidance;
    }

    /**
     * 같은 사유가 현재 기록일 때 또 감지되면 새 기록을 만들지 여부. false면 처리 중인 기록을 흔들지 않도록 무시한다.
     */
    public boolean repeatable() {
        return repeatable;
    }
}
