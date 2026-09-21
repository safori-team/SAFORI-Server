package com.safori.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeminiAnalysisResult(
        String transcript,
        String summary,
        String title,
        List<GeminiSegment> segments,
        @JsonProperty("stability_score") double stabilityScore
) {}
