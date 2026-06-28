package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "AI 감정 분석 오류 신고 요청")
@Getter
@NoArgsConstructor
public class ReportVoiceEmotionRequest {

    @Schema(description = "사용자가 실제로 느낀 대표 감정 (EmotionType enum)", example = "HAPPY")
    @NotNull(message = "대감정은 필수입니다.")
    private EmotionType reportedEmotion;

    @Schema(description = "신고 상세 메시지 (선택)", example = "AI가 분노로 분석했지만 실제로는 기쁨이었어요")
    private String message;
}
