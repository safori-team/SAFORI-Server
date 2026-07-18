package com.safori.domain.chatbot.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 트리거 판단에 필요한 마음일기 감정 이력 조회 포트.
 * 구현은 voice 도메인에 둔다 (chatbot → voice 단방향 의존).
 */
public interface DiaryEmotionHistoryPort {

    /**
     * 아직 세션이 만들어지지 않은 분석 완료 일기 ID 목록.
     *
     * @param since 이 시각 이후 분석 완료된 일기만 (스캔 범위 상한)
     * @param limit 최대 개수
     * @return 분석 완료 시각 오름차순 (오래된 것부터 처리)
     */
    List<Long> findUntriggeredVoiceIds(LocalDateTime since, int limit);

    /**
     * 지정 기간의 일별 대표 감정. 사용자 신고 감정이 있으면 그것을 우선 반영한다.
     *
     * @param from 시작일 (포함)
     * @param to   종료일 (포함)
     * @return 날짜 내림차순(최신 → 과거). 일기가 없는 날은 원소 자체가 없다.
     */
    List<DailyDiaryEmotion> findDailyEmotions(Long userId, LocalDate from, LocalDate to);
}
