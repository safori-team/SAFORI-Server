package com.safori.infra.sqs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 응답 큐(ResponseEmotionAnalysis)에서 받는 메시지 본문.
 *
 * <p>{@code request_id}·{@code clip_id}는 명세상 문자열/숫자 양쪽이 올 수 있어 Jackson의
 * 기본 강제 변환에 맡긴다. 계약에 필드가 추가돼도 기존 배포가 깨지지 않도록 미지의 필드는
 * 무시한다.
 *
 * @param analysisResult 성공이면 소분류 판정 결과, 4xx 실패면 오류 본문
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmotionAnalysisResponseMessage(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("clip_id") Long clipId,
        @JsonProperty("processing_status") String processingStatus,
        @JsonProperty("analysis_result") JsonNode analysisResult,
        @JsonProperty("request_key") String requestKey,
        @JsonProperty("response_key") String responseKey,
        @JsonProperty("completed_at") OffsetDateTime completedAt
) {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
