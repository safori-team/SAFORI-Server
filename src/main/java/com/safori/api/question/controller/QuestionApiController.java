package com.safori.api.question.controller;

import com.safori.api.common.dto.ApiResponseDto;
import com.safori.api.question.service.GetAllQuestionsUseCase;
import com.safori.api.question.service.GetRandomQuestionUseCase;
import com.safori.api.question.dto.QuestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "[질문]", description = "마음일기 녹음 시 제시할 질문 목록 조회 API.")
@RestController
@RequestMapping("/v1/api/users/questions")
@RequiredArgsConstructor
public class QuestionApiController {

    private final GetAllQuestionsUseCase getAllQuestionsUseCase;
    private final GetRandomQuestionUseCase getRandomQuestionUseCase;

    @Operation(summary = "전체 질문 목록 조회",
            description = "카테고리별 전체 질문 목록을 반환합니다. 마음일기 녹음 시 질문 선택 화면에 사용합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ApiResponseDto<List<QuestionResponse>> getAllQuestions() {
        return ApiResponseDto.onSuccess(getAllQuestionsUseCase.execute());
    }

    @Operation(summary = "랜덤 질문 1개 조회",
            description = "전체 질문 중 무작위로 1개를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/random")
    public ApiResponseDto<QuestionResponse> getRandomQuestion() {
        return ApiResponseDto.onSuccess(getRandomQuestionUseCase.execute());
    }
}
