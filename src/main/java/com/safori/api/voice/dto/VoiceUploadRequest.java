package com.safori.api.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Schema(description = "음성 메타데이터 저장 요청")
@Getter
public class VoiceUploadRequest {

    @Schema(description = "Presigned URL 발급 시 받은 S3 오브젝트 키", example = "voices/user01/abc123.m4a")
    @NotBlank
    private String voiceKey;

    @Schema(description = "음성 제목", example = "오늘의 마음일기")
    private String voiceTitle;

    @Schema(description = "음성 길이(초)", example = "37")
    private int duration;

    @Schema(description = "샘플레이트(Hz)", example = "44100")
    private int sampleRate;

    @Schema(description = "비트레이트(bps)", example = "128000")
    private int bitRate;
}
