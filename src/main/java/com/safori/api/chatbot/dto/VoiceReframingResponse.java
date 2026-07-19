package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "음성 리프레이밍 응답 (STT 텍스트 + 도란이 상담)")
public record VoiceReframingResponse(
        @Schema(description = "저장된 메시지 ID", example = "101")
        Long messageId,
        @Schema(description = "STT 전사 텍스트 (사용자 발화 말풍선 렌더링용)", example = "요즘 잠을 잘 못 자고 마음이 무거워요")
        String content,
        @Schema(description = "AI 공감 메시지", example = "정말 힘드셨겠어요.")
        String empathy,
        @Schema(description = "감지된 인지 왜곡 유형 (없으면 null)", example = "흑백논리")
        String detectedDistortion,
        @Schema(description = "AI 분석 내용", example = "이 상황에서 흑백논리적 사고 패턴이 나타났습니다.")
        String analysis,
        @Schema(description = "소크라테스식 탐색 질문", example = "그 상황에서 다른 가능성은 없었을까요?")
        String socraticQuestion,
        @Schema(description = "대안적 사고 제안", example = "완벽하지 않더라도 충분히 잘하고 있을 수 있어요.")
        String alternativeThought,
        @Schema(description = "AI가 감지한 감정", example = "슬픔")
        String emotion,
        @Schema(description = """
                이 응답으로 상담이 마무리됐는지 여부. true면 이 세션에는 더 이상 메시지를 보낼 수 없고
                (보내면 4206 CHAT_SESSION_CLOSED), socraticQuestion에는 질문 대신 마무리 말이 담긴다.""",
                example = "false")
        boolean sessionClosed
) {}
