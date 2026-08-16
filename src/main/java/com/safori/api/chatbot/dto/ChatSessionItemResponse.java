package com.safori.api.chatbot.dto;

import com.safori.domain.chatbot.entity.ChatReplyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "챗봇 세션 목록 항목")
public record ChatSessionItemResponse(
        @Schema(description = "세션 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId,
        @Schema(description = "마지막 메시지 미리보기", example = "그렇군요, 조금 더 말씀해 주실 수 있나요?")
        String lastMessage,
        @Schema(description = "마지막 업데이트 시각", example = "2024-01-15T09:30:00")
        LocalDateTime lastUpdated,
        @Schema(description = "감지된 인지 왜곡 태그 목록", example = "[\"흑백논리\", \"과잉일반화\"]")
        List<String> distortionTags,
        @Schema(description = "세션 대표 감정", example = "슬픔")
        String emotion,
        @Schema(description = """
                상담이 마무리된 세션인지. true면 이어서 대화할 수 없다.
                턴 소진과 위기 가드레일 두 경로 모두 true다.""", example = "false")
        boolean sessionClosed,
        @Schema(description = "위기 가드레일에 걸려 중단된 세션인지. true면 sessionClosed도 항상 true다.",
                example = "false")
        boolean crisisDetected,
        @Schema(description = """
                최신 메시지의 도란이 응답 생성 상태 (PROCESSING / COMPLETED / FAILED).
                PROCESSING이면 lastMessage는 사용자 발화이고 distortionTags·emotion은 비어 있다.""",
                example = "COMPLETED")
        ChatReplyStatus replyStatus
) {}
