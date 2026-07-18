package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 채팅 상세 응답.
 *
 * <p>페이징 단위는 "대화 턴"이다. 한 턴(DB row 1개)은 사용자 발화 + 도란이 응답 한 쌍이며,
 * {@code items}에는 두 개의 항목으로 펼쳐져 나온다. 따라서 {@code items.size()}는
 * {@code size}의 최대 2배가 될 수 있고, {@code totalElements}와도 단위가 다르다.
 * 페이지 계산은 반드시 턴 기준({@code totalElements}, {@code hasNext})으로 해야 한다.
 */
@Schema(description = "채팅 내역 응답 (페이징 단위는 대화 턴)")
public record ChatHistoryResponse(
        @Schema(description = "세션 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId,
        @Schema(description = """
                메시지 목록. 페이지 안에서 생성 시각 오름차순.
                한 턴이 user/assistant 두 항목으로 펼쳐지므로 최대 size*2 개가 담긴다.
                (마음일기 트리거 턴은 user 발화가 없어 assistant 항목만 나온다)""")
        List<ChatMessageItemResponse> items,
        @Schema(description = "현재 페이지 번호 (1부터 시작). page=1이 가장 최근 대화다.", example = "1")
        int page,
        @Schema(description = "페이지당 대화 턴 수 (items 개수가 아님)", example = "20")
        int size,
        @Schema(description = "전체 대화 턴 수 (items 항목 수가 아님 — 최대 2배로 펼쳐짐)", example = "45")
        long totalElements,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages,
        @Schema(description = "다음 페이지(더 과거 대화) 존재 여부", example = "true")
        boolean hasNext,
        @Schema(description = """
                상담이 마무리된 세션인지. true면 입력창을 비활성화한다.
                (메시지 전송 시 4206 CHAT_SESSION_CLOSED)""", example = "false")
        boolean sessionClosed
) {}
