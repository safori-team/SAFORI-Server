package com.safori.api.operator.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.dev.dto.SeedMindDiaryResponse;
import com.safori.api.dev.service.SeedMindDiaryUseCase;
import com.safori.api.operator.dto.OperatorMindDiaryStatusResponse;
import com.safori.api.operator.dto.TriggerMindDiaryOfferResponse;
import com.safori.api.operator.service.OperatorMindDiaryUseCase;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.question.entity.QuestionCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

import static com.safori.security.filter.OperatorKeyFilter.HEADER;

/**
 * [개발/테스트 전용] 운영자 백오피스에서 사용자 대신 마음일기를 심고 상담 제안을 바로 만든다.
 *
 * <p>사용자 JWT 가 필요한 {@code /v1/api/dev/mind-diary} 와 같은 일을 운영자 키로 한다 — 백오피스는
 * 사용자 비밀번호 없이 아이디만으로 테스트 계정을 준비할 수 있다.
 * {@code @Profile("!prod")} — 운영 프로파일에서는 빈 자체가 만들어지지 않는다.
 */
@Tag(name = "operator-dev-mind-diary",
     extensions = @Extension(properties = @ExtensionProperty(name = "x-displayName", value = "[운영자 - 개발용 마음일기]")),
     description = "테스트 계정에 마음일기를 심고 상담 제안을 즉시 만드는 개발 도구. `X-Operator-Key` 로 인증한다. 운영(prod)에서는 비활성화된다.")
@Validated
@RestController
@Profile("!prod")
@RequiredArgsConstructor
@RequestMapping("/v1/api/operator/dev/users/{username}")
public class OperatorDevMindDiaryController {

    private final SeedMindDiaryUseCase seedMindDiaryUseCase;
    private final OperatorMindDiaryUseCase operatorMindDiaryUseCase;

    @Operation(operationId = "getOperatorMindDiaryStatus", summary = "[개발용] 최근 마음일기·상담 제안 상태",
            description = "최근 `days` 일의 일기(대표 감정·제안 상태), 오늘까지의 연속 부정 일수, 대기 중인 제안을 줍니다.",
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "400", description = "4052: 존재하지 않는 유저")
    @GetMapping("/mind-diaries")
    public ApiResponseDto<OperatorMindDiaryStatusResponse> status(
            @PathVariable String username,
            @Parameter(description = "조회 기간 (오늘 포함 일수)") @RequestParam(defaultValue = "7") @Min(1) @Max(31) int days) {
        return ApiResponseDto.onSuccess(operatorMindDiaryUseCase.status(username, days));
    }

    @Operation(operationId = "seedOperatorMindDiary", summary = "[개발용] 마음일기 시딩",
            description = """
                    `/v1/api/dev/mind-diary` 와 같다 — 서버가 오디오를 S3 에 올리고 실제 분석까지 돌린 뒤 작성일을 심는다.
                    `emotion` 을 주면 대표 감정만 덮어쓴다(내용은 실제 분석값). 1일 1일기 제약은 건너뛴다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "400", description = "4052: 존재하지 않는 유저")
    @PostMapping(value = "/mind-diaries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<SeedMindDiaryResponse> seed(
            @PathVariable String username,
            @Parameter(description = "오디오 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "작성일 yyyy-MM-dd") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "대표 감정 강제 지정 (선택)") @RequestParam(required = false) EmotionType emotion,
            @Parameter(description = "질문 카테고리 (선택)") @RequestParam(required = false) QuestionCategory questionCategory,
            @Parameter(description = "질문 인덱스 (선택)") @RequestParam(required = false) Integer questionIndex
    ) throws IOException {
        return ApiResponseDto.onSuccess(seedMindDiaryUseCase.execute(
                username, date, questionCategory, questionIndex, emotion,
                file.getBytes(), resolveExtension(file.getOriginalFilename()), file.getContentType()));
    }

    @Operation(operationId = "triggerOperatorMindDiaryOffer", summary = "[개발용] 상담 제안 즉시 평가",
            description = """
                    스케줄러 주기를 기다리지 않고 이 사용자의 최신 미트리거 일기부터 조건을 평가해, 처음 충족하는 일기로
                    제안(OFFERED)을 하나 만든다. 판정 기준·원장·제안 푸시는 스케줄러와 같다.
                    조건을 못 채우면 `offered=false` 와 최신 일기의 보류 사유를 준다.
                    """,
            parameters = @Parameter(name = HEADER, in = ParameterIn.HEADER, required = true, description = "운영자 키"))
    @ApiResponse(responseCode = "400", description = "4052: 존재하지 않는 유저")
    @PostMapping("/mind-diary-offer")
    public ApiResponseDto<TriggerMindDiaryOfferResponse> triggerOffer(@PathVariable String username) {
        return ApiResponseDto.onSuccess(operatorMindDiaryUseCase.triggerOffer(username));
    }

    private String resolveExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "m4a";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
