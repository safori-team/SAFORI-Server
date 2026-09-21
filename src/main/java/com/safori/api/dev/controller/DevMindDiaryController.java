package com.safori.api.dev.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.dev.dto.SeedMindDiaryResponse;
import com.safori.api.dev.service.SeedMindDiaryUseCase;
import com.safori.common.annotation.UserCode;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.question.entity.QuestionCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * [개발/테스트 전용] 마음일기 시딩 API.
 *
 * <p>{@code @Profile("!prod")} — 운영 프로파일에서는 빈 자체가 만들어지지 않아 노출되지 않는다.
 */
@Slf4j
@Tag(name = "[개발용 - 마음일기 시딩]",
     description = """
             스케줄러의 '3일 연속 부정 감정' 조건을 결정적으로 재현하기 위한 개발 도구.
             서버가 오디오를 S3에 올리고(프론트 없이) 실제 분석까지 돌린 뒤, 원하는 날짜로
             작성일을 심는다. topEmotion은 선택적으로 덮어써 스트릭을 만든다.
             운영(prod) 프로파일에서는 비활성화된다.
             """)
@RestController
@Profile("!prod")
@RequiredArgsConstructor
@RequestMapping("/v1/api/dev/mind-diary")
public class DevMindDiaryController {

    private final SeedMindDiaryUseCase seedMindDiaryUseCase;

    @Operation(summary = "[개발용] 마음일기 시딩 (오디오 업로드 + 날짜/감정 지정)",
            description = """
                    multipart/form-data로 오디오를 올리면 서버가 S3 PUT → 등록 → 실제 분석까지 수행한다.

                    파라미터:
                      • file    (필수) - 오디오 파일 (m4a/mp3 등)
                      • date    (필수) - 작성일 (yyyy-MM-dd). 이 날짜로 스트릭을 판정한다.
                      • emotion (선택) - 대표 감정 강제 지정 (HAPPY/SAD/NEUTRAL/ANGRY/ANXIETY/SURPRISE).
                                        비우면 실제 분석 감정을 그대로 쓴다. 내용(전사/라벨)은 항상 실제값.
                      • questionCategory / questionIndex (선택) - 질문 매핑. 비우면 자유 일기.

                    사용 예: SAD로 3일치(예: 7/15, 7/16, 7/17) 시딩하면 다음 스케줄 주기에
                    7/17 일기가 3일 연속 부정으로 인식되어 상담 제안(OFFERED)이 생성된다.
                    """)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<SeedMindDiaryResponse> seed(
            @UserCode String username,
            @Parameter(description = "오디오 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "작성일 yyyy-MM-dd")
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "대표 감정 강제 지정 (선택)")
            @RequestParam(required = false) EmotionType emotion,
            @Parameter(description = "질문 카테고리 (선택)")
            @RequestParam(required = false) QuestionCategory questionCategory,
            @Parameter(description = "질문 인덱스 (선택)")
            @RequestParam(required = false) Integer questionIndex
    ) throws IOException {
        String extension = resolveExtension(file.getOriginalFilename());
        SeedMindDiaryResponse result = seedMindDiaryUseCase.execute(
                username, date, questionCategory, questionIndex, emotion,
                file.getBytes(), extension, file.getContentType());
        return ApiResponseDto.onSuccess(result);
    }

    private String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "m4a";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
