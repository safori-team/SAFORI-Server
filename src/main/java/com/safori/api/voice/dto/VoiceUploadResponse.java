package com.safori.api.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "음성 메타데이터 저장 응답")
@Getter
@Builder
@AllArgsConstructor
public class VoiceUploadResponse {
    @Schema(description = "저장된 음성 ID", example = "1")
    private final Long voiceId;
}
