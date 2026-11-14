package com.safori.api.operator.dto;

import com.safori.api.chatbot.dto.MindDiaryOfferResponse;
import com.safori.domain.chatbot.entity.MindDiaryTriggerStatus;
import com.safori.domain.emotion.entity.EmotionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "[개발용] 사용자의 최근 마음일기와 상담 제안 상태")
public record OperatorMindDiaryStatusResponse(
        @Schema(description = "사용자 아이디", example = "seung1104") String username,
        @Schema(description = "상담 제안에 필요한 연속 부정 일수 (서버 설정)", example = "3") int requiredConsecutiveDays,
        @Schema(description = "부정으로 취급하는 감정 (서버 설정)") List<EmotionType> negativeEmotions,
        @Schema(description = "오늘까지 끊기지 않고 이어진 부정 일수. 오늘 일기가 없으면 0", example = "2")
        int negativeStreakDays,
        @Schema(description = "조회 기간의 일기. 작성일 내림차순") List<Diary> diaries,
        @Schema(description = "대기 중인 상담 제안 (앱 모달 기준)") MindDiaryOfferResponse offer
) {

    public record Diary(
            @Schema(description = "일기(voice) ID", example = "16") Long voiceId,
            @Schema(description = "작성일 (스트릭 판정 기준)", example = "2026-10-16") LocalDate date,
            @Schema(description = "대표 감정 (사용자 신고 우선). 분석 전이면 null") EmotionType emotion,
            @Schema(description = "부정 감정인지") boolean negative,
            @Schema(description = "이 일기로 만든 상담 제안 상태. 없으면 null") MindDiaryTriggerStatus offerStatus) {
    }
}
