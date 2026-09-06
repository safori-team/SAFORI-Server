package com.safori.api.tts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "TTS 합성 응답")
public record TtsSynthesisResponse(
        @Schema(description = "MP3 presigned GET URL. 클라이언트가 그대로 재생한다.",
                example = "https://bucket.s3.ap-northeast-2.amazonaws.com/tts/...&X-Amz-Signature=...")
        String audioUrl,

        @Schema(description = "S3 캐시 히트 여부. 모니터링·비용 추적용", example = "true")
        boolean cached,

        @Schema(description = "audioUrl 유효기간(초)", example = "3600")
        int expiresInSeconds
) {}
