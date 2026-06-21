package com.safori.api.voice.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.voice.dto.PresignedUrlResponse;
import com.safori.api.voice.service.DeleteVoiceUseCase;
import com.safori.api.voice.service.GenerateVoicePresignedUrlUseCase;
import com.safori.api.voice.service.UploadVoiceFileUseCase;
import com.safori.common.annotation.UserCode;
import com.safori.domain.question.entity.QuestionCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[마음일기(음성)]", description = "마음일기(음성) 업로드 · 등록 · 삭제 API.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users/voices")
public class VoiceApiController {

    private final GenerateVoicePresignedUrlUseCase generateVoicePresignedUrlUseCase;
    private final UploadVoiceFileUseCase uploadVoiceFileUseCase;
    private final DeleteVoiceUseCase deleteVoiceUseCase;

    @Operation(summary = "음성 파일 업로드용 Presigned URL 발급",
            description = "S3에 음성 파일을 직접 업로드하기 위한 Presigned PUT URL과 voiceKey를 발급합니다. (유효시간 10분)")
    @ApiResponse(responseCode = "200", description = "presignedUrl · voiceKey 반환")
    @GetMapping("/presigned-url")
    public ApiResponseDto<PresignedUrlResponse> getPresignedUrl(@UserCode String username,
                                                                @Parameter(description = "파일 확장자 (예: m4a, mp3)")
                                                                @RequestParam String extension) {
        return ApiResponseDto.onSuccess(generateVoicePresignedUrlUseCase.execute(username, extension));
    }

    @Operation(summary = "음성 파일 업로드 완료 등록",
            description = "S3 업로드 완료 후 voiceKey와 질문(카테고리/인덱스)을 함께 등록합니다. 생성된 voiceId를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "등록 성공 — 생성된 voiceId 반환")
    @ApiResponse(responseCode = "400", description = "- `4100`: 존재하지 않는 질문입니다")
    @PostMapping
    public ApiResponseDto<Long> uploadVoiceWithQuestion(@UserCode String username,
                                                        @Parameter(description = "질문 카테고리 (enum)")
                                                        @RequestParam QuestionCategory questionCategory,
                                                        @Parameter(description = "질문 인덱스 번호")
                                                        @RequestParam int questionIndex,
                                                        @Parameter(description = "S3 업로드 완료 후 받은 voiceKey")
                                                        @RequestParam String voiceKey) {
        return ApiResponseDto.onSuccess(
                uploadVoiceFileUseCase.execute(username, questionCategory, questionIndex, voiceKey));
    }

    @Operation(summary = "마음일기 삭제",
            description = "본인 소유의 음성을 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "삭제 성공 — result: null")
    @ApiResponse(responseCode = "400", description = "- `4150`: 존재하지 않는 음성파일 / `4151`: 접근권한 없음")
    @DeleteMapping("/{voiceId}")
    public ApiResponseDto<Void> deleteUserVoice(@PathVariable Long voiceId,
                                                @UserCode String username) {
        deleteVoiceUseCase.execute(voiceId, username);
        return ApiResponseDto.onSuccess(null);
    }
}
