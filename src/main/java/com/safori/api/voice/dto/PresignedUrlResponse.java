package com.safori.api.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "S3 Presigned URL 발급 응답")
@Getter
@Builder
@AllArgsConstructor
public class PresignedUrlResponse {
    @Schema(description = "음성 파일을 PUT 방식으로 직접 업로드할 S3 URL (유효시간 10분)", example = "https://s3.amazonaws.com/bucket/...")
    private final String presignedUrl;
    @Schema(description = "업로드 완료 후 POST /v1/api/users/voices 호출 시 전달할 S3 오브젝트 키", example = "voices/user01/abc123.m4a")
    private final String voiceKey;
}
