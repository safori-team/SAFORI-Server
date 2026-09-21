package com.safori.api.chatbot.dto;

import com.safori.domain.chatbot.entity.ChatReplyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "채팅 메시지 항목")
public record ChatMessageItemResponse(
        @Schema(description = "메시지 ID", example = "101")
        Long messageId,
        @Schema(description = "메시지 발신자 역할 (USER / ASSISTANT)", example = "ASSISTANT")
        String role,
        @Schema(description = "메시지 내용", example = "오늘 힘드셨군요. 조금 더 말씀해 주실 수 있나요?")
        String content,
        @Schema(description = "연결된 마음일기 ID (없으면 null)", example = "42")
        Long voiceId,
        @Schema(description = "음성 발화의 S3 오디오 키 (USER_VOICE 메시지만, 다시 듣기용). 없으면 null",
                example = "voices/2026/05/abc123.m4a")
        String voiceKey,
        @Schema(description = "AI 공감 메시지", example = "정말 힘드셨겠어요.")
        String empathy,
        @Schema(description = "감지된 인지 왜곡 유형", example = "흑백논리")
        String detectedDistortion,
        @Schema(description = "AI 분석 내용", example = "이 상황에서 흑백논리적 사고 패턴이 나타났습니다.")
        String analysis,
        @Schema(description = "소크라테스식 탐색 질문", example = "그 상황에서 다른 가능성은 없었을까요?")
        String socraticQuestion,
        @Schema(description = "대안적 사고 제안", example = "완벽하지 않더라도 충분히 잘하고 있을 수 있어요.")
        String alternativeThought,
        @Schema(description = "AI가 감지한 감정", example = "슬픔")
        String emotion,
        @Schema(description = "사용자 피드백 감정", example = "공감됨")
        String feedbackEmotion,
        @Schema(description = "사용자 피드백 상세", example = "정확히 제가 느낀 감정이에요.")
        String feedbackDetail,
        @Schema(description = "피드백 입력 시각 (피드백 없으면 null)", example = "2024-01-15T09:35:00")
        LocalDateTime feedbackAt,
        @Schema(description = "메시지 생성 시각", example = "2024-01-15T09:30:00")
        LocalDateTime timestamp,
        @Schema(description = """
                도란이 응답 생성 상태 (PROCESSING / COMPLETED / FAILED).
                role=user 항목은 항상 null.

                • PROCESSING - 도란이 6개 필드가 전부 null. "생각 중" 말풍선으로 렌더한다.
                • FAILED     - 6개 필드에 폴백 문구가 채워져 있다(null 아님). 재시도 버튼을 노출하고
                               피드백 버튼은 숨긴다.

                null 여부가 아니라 이 값으로 분기해야 한다.""", example = "COMPLETED")
        ChatReplyStatus replyStatus
) {}
