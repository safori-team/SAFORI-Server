package com.safori.api.voice.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.voice.dto.PresignedUrlResponse;
import com.safori.api.voice.service.GenerateVoicePresignedUrlUseCase;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[음성]", description = "음성 파일 업로드 및 메타데이터 저장 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/voices")
public class VoiceApiController {

    private final GenerateVoicePresignedUrlUseCase generateVoicePresignedUrlUseCase;

    @Operation(summary = "음성 업로드용 Presigned URL 발급",
            description = "클라이언트가 음성 파일을 S3에 직접 PUT 업로드할 Presigned URL과 voiceKey를 발급합니다. (보호 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "발급 성공 — presignedUrl + voiceKey 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @GetMapping("/presigned-url")
    public ApiResponseDto<PresignedUrlResponse> generatePresignedUrl(@UserCode String username,
                                                                     @RequestParam String extension) {
        return ApiResponseDto.onSuccess(generateVoicePresignedUrlUseCase.execute(username, extension));
    }
}
