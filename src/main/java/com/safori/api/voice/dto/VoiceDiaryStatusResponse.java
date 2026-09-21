package com.safori.api.voice.dto;

import com.safori.domain.voice.entity.Voice;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 분석 처리 상태 응답 (폴링용)")
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class VoiceDiaryStatusResponse {

    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "감정 분석 상태 (PROCESSING / COMPLETED / FAILED). 업로드 직후부터 PROCESSING이다.", example = "COMPLETED")
    private final Voice.AnalysisStatus diaryStatus;
}
