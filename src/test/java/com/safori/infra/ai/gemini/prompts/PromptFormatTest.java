package com.safori.infra.ai.gemini.prompts;

import com.safori.domain.chatbot.model.HistoryTurn;
import com.safori.domain.chatbot.model.MindDiaryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 프롬프트 템플릿의 {@code %s} 개수와 formatted() 인자 개수가 맞는지 런타임으로 검증한다.
 * 불일치면 {@code MissingFormatArgumentException}이 나므로, 빌더가 예외 없이 도는 것만으로 충분.
 * 아울러 분량 지침(LENGTH_GUIDE)이 실제로 주입되는지 확인한다.
 */
class PromptFormatTest {

    private final MindDiaryEntry entry = new MindDiaryEntry(
            "2026-07-17T09:00", "(자유 일기)", "오늘 힘들었어요", "- 주된 감정: sad", "sad");
    private final List<HistoryTurn> history = List.of(new HistoryTurn("안녕", "네 말씀하세요"));

    @Test
    @DisplayName("MindDiaryPrompt — 단일/다중 일기 모두 포맷 예외 없이 빌드되고 분량 지침 포함")
    void mindDiaryBuilds() {
        assertThatCode(() -> {
            String single = MindDiaryPrompt.build("홍길동 할머니", List.of(entry));
            String multi = MindDiaryPrompt.build("홍길동 할머니", List.of(entry, entry, entry));
            assertThat(single).contains("분량 지침").contains("홍길동 할머니");
            assertThat(multi).contains("분량 지침").contains("홍길동 할머니");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ReframingPrompt — 일반 턴/마지막 턴 모두 포맷 예외 없이 빌드되고 분량 지침 포함")
    void reframingBuilds() {
        assertThatCode(() -> {
            String normal = ReframingPrompt.build("오늘 힘들어요", history, 2, "sad", "홍길동 할아버지", 4, false);
            String last = ReframingPrompt.build("이제 괜찮아요", history, 4, "sad", "홍길동 할아버지", 4, true);
            assertThat(normal).contains("분량 지침");
            assertThat(last).contains("분량 지침").contains("상담 마무리");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VoiceReframingPrompt — 일반 턴/마지막 턴 모두 포맷 예외 없이 빌드되고 분량 지침 포함")
    void voiceReframingBuilds() {
        assertThatCode(() -> {
            String normal = VoiceReframingPrompt.build(
                    "오늘 힘들어요", history, 1, "홍길동", "- 주된 감정: sad", "sad", 4, false);
            String last = VoiceReframingPrompt.build(
                    "이제 괜찮아요", history, 4, "홍길동", "- 주된 감정: sad", "sad", 4, true);
            assertThat(normal).contains("분량 지침");
            assertThat(last).contains("분량 지침").contains("상담 마무리");
        }).doesNotThrowAnyException();
    }
}
