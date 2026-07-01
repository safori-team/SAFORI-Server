package com.safori.api.emotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "주별 감정 분석 종합 응답")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class WeeklyAnalysisCombinedResponse {

    @Schema(description = "요일별 감정 목록 (7개, 일기 없는 날은 emotionType/voiceId=null)")
    private final List<WeekDayEmotion> weeklyEmotions;
    @Schema(description = "AI 생성 주간 감정 리포트 메시지", example = "이번 주는 전반적으로 안정적인 감정을 유지했어요.")
    private final String reportMessage;
}
