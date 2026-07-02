package com.safori.api.emotion.controller;

import com.safori.common.annotation.UserCode;
import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.emotion.dto.EmotionLabelDiaryListResponse;
import com.safori.api.emotion.dto.MonthlyAnalysisCombinedResponse;
import com.safori.api.emotion.dto.MonthlyEmotionBubbleResponse;
import com.safori.api.emotion.dto.WeekDayEmotion;
import com.safori.api.emotion.dto.WeeklyAnalysisCombinedResponse;

import java.util.List;
import com.safori.api.emotion.service.GetEmotionLabelDiaryListUseCase;
import com.safori.api.emotion.service.GetMonthlyEmotionBubbleUseCase;
import com.safori.api.voice.service.GetMonthlyEmotionAnalysisUseCase;
import com.safori.api.voice.service.GetWeeklyEmotionAnalysisUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[감정 분석]", description = """
        월간 · 주간 감정 분석 집계 API.

        마음일기들의 감정 데이터를 기간별로 집계하여 통계를 제공합니다.
        `yearMonth` 파라미터는 `yyyy-MM` 형식입니다 (예: 2024-01).
        """)
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users/voices/analyzing")
public class EmotionAnalysisApiController {

    private final GetMonthlyEmotionAnalysisUseCase getMonthlyEmotionAnalysisUseCase;
    private final GetWeeklyEmotionAnalysisUseCase getWeeklyEmotionAnalysisUseCase;
    private final GetMonthlyEmotionBubbleUseCase getMonthlyEmotionBubbleUseCase;
    private final GetEmotionLabelDiaryListUseCase getEmotionLabelDiaryListUseCase;

    @Operation(summary = "월간 감정 분석 조회",
            description = """
                    특정 월의 감정별 일기 수, 대표 감정, 총 일기 수, AI 리포트 메시지를 반환합니다.
                    `yearMonth` 또는 구버전 파라미터 `month` 중 하나를 전달하세요.
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/monthly")
    public ApiResponseDto<MonthlyAnalysisCombinedResponse> getCareEmotionMonthly(
            @UserCode String username,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String month) {
        return ApiResponseDto.onSuccess(
                getMonthlyEmotionAnalysisUseCase.execute(username, resolveYearMonth(yearMonth, month)));
    }

    @Operation(summary = "이번 주 일별 감정 조회 (홈화면용)",
            description = "이번 주(일~토) 각 날짜의 감정과 voiceId를 반환합니다. AI 리포트는 포함되지 않습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/weekly/current")
    public ApiResponseDto<List<WeekDayEmotion>> getCurrentWeekEmotions(@UserCode String username) {
        return ApiResponseDto.onSuccess(getWeeklyEmotionAnalysisUseCase.executeCurrentWeek(username));
    }

    @Operation(summary = "주간 감정 분석 조회",
            description = """
                    특정 월의 특정 주차(week) 일별 감정·voiceId와 AI 리포트를 반환합니다.
                    `week`는 해당 월의 주차 번호입니다 (1부터 시작).
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "400", description = "- `4003`: 유효하지 않은 주차 값입니다")
    @GetMapping("/weekly")
    public ApiResponseDto<WeeklyAnalysisCombinedResponse> getCareEmotionWeekly(
            @UserCode String username,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String month,
            @RequestParam int week) {
        return ApiResponseDto.onSuccess(
                getWeeklyEmotionAnalysisUseCase.execute(username, resolveYearMonth(yearMonth, month), week));
    }

    @Operation(summary = "월간 감정 버블차트 데이터 조회",
            description = "특정 월에 등장한 세부 감정 레이블별 일기 수와 강도를 반환합니다. 버블차트 렌더링에 사용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/bubble")
    public ApiResponseDto<MonthlyEmotionBubbleResponse> getMonthlyEmotionBubble(
            @UserCode String username,
            @RequestParam String yearMonth) {
        return ApiResponseDto.onSuccess(getMonthlyEmotionBubbleUseCase.execute(username, yearMonth));
    }

    @Operation(summary = "감정 레이블별 일기 목록 조회",
            description = """
                    특정 월에 특정 세부 감정(label)이 기록된 일기 목록을 반환합니다.
                    `label`은 Gemini 세부 감정 레이블입니다 (예: joy, anxiety, frustration).
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/bubble/diaries")
    public ApiResponseDto<EmotionLabelDiaryListResponse> getEmotionLabelDiaries(
            @UserCode String username,
            @RequestParam String yearMonth,
            @RequestParam String label) {
        return ApiResponseDto.onSuccess(getEmotionLabelDiaryListUseCase.execute(username, yearMonth, label));
    }

    /**
     * yearMonth(신규) 또는 month(구버전 호환) 중 하나를 받아 유효한 값을 반환.
     * 둘 다 null이면 필수 파라미터 누락으로 예외를 발생시킨다.
     */
    private String resolveYearMonth(String yearMonth, String month) {
        if (yearMonth != null) return yearMonth;
        if (month != null) return month;
        throw new IllegalArgumentException("필수 파라미터 'yearMonth'가 누락되었습니다.");
    }
}
