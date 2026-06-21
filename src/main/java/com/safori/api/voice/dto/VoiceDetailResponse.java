package com.safori.api.voice.dto;

import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.Voice;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Schema(description = "마음일기 상세 응답")
@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceDetailResponse {
    @Schema(description = "마음일기 ID", example = "42")
    private final Long voiceId;
    @Schema(description = "일기 작성 날짜", example = "2024-01-15")
    private final LocalDate createdAt;
    @Schema(description = "감정 분석 상태 (PENDING / PROCESSING / COMPLETED / FAILED)", example = "COMPLETED")
    private final Voice.AnalysisStatus analysisStatus;
    @Schema(description = "대표 감정 (분석 완료 전 null)", example = "HAPPY")
    private final EmotionType topEmotion;
    @Schema(description = "연결된 질문 제목 (질문 없으면 null)", example = "오늘 가장 기억에 남는 순간은?")
    private final String questionTitle;
    @Schema(description = "STT 변환 텍스트 (분석 완료 전 null)", example = "오늘 친구와 오랜만에 만나서 정말 즐거웠어요.")
    private final String content;
    @Schema(description = "음성 파일 S3 URL (다운로드용 presigned URL)", example = "https://s3.amazonaws.com/bucket/voices/user01/abc.m4a?...")
    private final String s3Url;
    @Schema(description = "챗봇 세션 상태 (pending / ready / failed). 세션 없으면 null", example = "ready")
    private final String chatStatus;
    @Schema(description = "챗봇 세션 ID (chatStatus=COMPLETED 일 때 유효)", example = "550e8400-e29b-41d4-a716-446655440000")
    private final String sessionId;
    @Schema(description = "사용자가 신고한 실제 감정 (신고 없으면 null)", example = "SAD")
    private final EmotionType reportedEmotion;
    @Schema(description = "신고 상세 메시지 (신고 없으면 null)", example = "실제로는 슬픔을 더 크게 느꼈습니다.")
    private final String reportMessage;
}
