package com.safori.api.voice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 목록 응답")
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceListResponse {
    @Schema(description = "마음일기 목록")
    @Builder.Default
    private final List<VoiceListItem> voices = new ArrayList<>();
}
