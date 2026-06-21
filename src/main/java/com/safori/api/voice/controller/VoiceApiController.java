package com.safori.api.voice.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.voice.dto.PresignedUrlResponse;
import com.safori.api.voice.dto.VoiceUploadRequest;
import com.safori.api.voice.dto.VoiceUploadResponse;
import com.safori.api.voice.service.DeleteVoiceUseCase;
import com.safori.api.voice.service.GenerateVoicePresignedUrlUseCase;
import com.safori.api.voice.service.UploadVoiceFileUseCase;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[음성]", description = "음성 파일 업로드 및 메타데이터 저장 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/voices")
public class VoiceApiController {

    private final GenerateVoicePresignedUrlUseCase generateVoicePresignedUrlUseCase;
    private final UploadVoiceFileUseCase uploadVoiceFileUseCase;
    private final DeleteVoiceUseCase deleteVoiceUseCase;

    @Operation(summary = "음성 업로드용 Presigned URL 발급",
            description = "클라이언트가 음성 파일을 S3에 직접 PUT 업로드할 Presigned URL과 voiceKey를 발급합니다. (보호 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "발급 성공 — presignedUrl + voiceKey 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @GetMapping("/presigned-url")
    public ApiResponseDto<PresignedUrlResponse> generatePresignedUrl(@UserCode String username,
                                                                     @RequestParam String extension) {
        return ApiResponseDto.onSuccess(generateVoicePresignedUrlUseCase.execute(username, extension));
    }

    @Operation(summary = "음성 메타데이터 저장",
            description = "S3 업로드 완료 후 voiceKey와 메타데이터(길이/포맷 등)를 저장합니다. 생성된 voiceId를 반환합니다. (보호 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "저장 성공 — voiceId 반환")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @PostMapping
    public ApiResponseDto<VoiceUploadResponse> uploadVoice(@UserCode String username,
                                                           @Valid @RequestBody VoiceUploadRequest request) {
        return ApiResponseDto.onSuccess(uploadVoiceFileUseCase.execute(username, request));
    }

    @Operation(summary = "음성 삭제",
            description = "본인 소유의 음성을 삭제합니다. (보호 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "삭제 성공")
    @ApiResponse(responseCode = "400", description = "- `4150`: 존재하지 않는 음성파일 / `4151`: 접근권한 없음")
    @ApiResponse(responseCode = "401", description = "로그인 필요 (유효한 토큰 없음)")
    @DeleteMapping("/{voiceId}")
    public ApiResponseDto<Void> deleteVoice(@UserCode String username,
                                            @PathVariable Long voiceId) {
        deleteVoiceUseCase.execute(voiceId, username);
        return ApiResponseDto.onSuccess(null);
    }
}
