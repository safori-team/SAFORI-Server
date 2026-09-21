package com.safori.domain.chatbot.model;

/**
 * 문맥 분류기가 반환한 위기 평가. 원문이나 모델의 자유 서술은 보관하지 않고 운영용 코드만 남긴다.
 */
public record CrisisAssessment(
        CrisisLevel level,
        boolean current,
        boolean hasPlan,
        boolean hasMeans,
        double confidence,
        String reasonCode
) {
    public static CrisisAssessment unavailable() {
        return new CrisisAssessment(CrisisLevel.UNKNOWN, false, false, false, 0.0, "UNAVAILABLE");
    }

    public boolean requiresCrisisFlow() {
        if (!current || level == null || !level.requiresCrisisFlow()) {
            return false;
        }
        // "다 뒤져라" 같은 욕설·저주를 실제 타해 실행 의도로 오인하지 않는다.
        // 타해는 현재 의도에 더해 구체적 계획 또는 수단이 확인된 경우에만 위기 흐름으로 보낸다.
        return level != CrisisLevel.HARM_TO_OTHERS || hasPlan || hasMeans;
    }
}
