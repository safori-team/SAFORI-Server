package com.safori.infra.sqs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 요청 큐(RequestEmotionAnalysis)로 보내는 메시지 본문.
 *
 * <p>필드명은 연동 명세의 snake_case를 따르되, 전역 ObjectMapper 설정(기존 API 응답은 전부
 * camelCase)에 영향을 주지 않도록 {@code @JsonProperty}로만 매핑한다.
 *
 * @param requestedAt ISO-8601 문자열. {@code OffsetDateTime}으로 두면 직렬화 결과가 전역
 *                    Jackson 설정에 따라 epoch 숫자로 바뀔 수 있고, 그러면 Wrapper의
 *                    {@code fromisoformat}이 실패해 응답 없이 재시도만 돌다 사라진다.
 * @param geminiResult 계약 형상({@code transcript/summary/prosody/major/detected})으로 추린
 *                     Gemini 분석 결과. 분석 Lambda가 {@code major}를 검증한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmotionAnalysisRequestMessage(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("clip_id") Long clipId,
        @JsonProperty("requested_at") String requestedAt,
        @JsonProperty("gemini_result") JsonNode geminiResult
) {}
