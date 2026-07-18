package com.safori.infra.ai.gemini.prompts;

import com.safori.domain.chatbot.model.MindDiaryEntry;

import java.util.List;

public final class MindDiaryPrompt {

    private MindDiaryPrompt() {}

    /**
     * 마음일기 기반 첫 대화 프롬프트.
     *
     * @param userName 사용자 이름
     * @param entries  시간순(오래된 → 최신) 일기 목록. 마지막이 대화를 트리거한 일기다.
     *                 트리거 정책에 따라 1건일 수도, 연속 부정 감정 구간 전체일 수도 있다.
     */
    public static String build(String userName, List<MindDiaryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries is null or empty");
        }
        MindDiaryEntry trigger = entries.get(entries.size() - 1);
        String userNameDisplay = (userName == null || userName.isBlank()) ? "내담자" : userName;

        return """
                당신은 전문 심리상담사이자 CBT(인지행동치료) 전문가 '도란이'입니다.
                사용자 '%s'님이 작성한 '마음일기'를 읽고, 먼저 다가가서 대화를 시작해야 합니다.

                %s
                %s

                %s

                **지시사항:**
                1. 복합 감정 읽기: 두드러지는 다른 감정도 함께 읽기.
                2. 공감(Empathy): 사용자 이름을 부르며 따뜻한 첫인사.
                3. 왜곡 탐지: 1~10번에 해당하면 명칭 기입, 긍정적이면 '긍정 정서 강화', 별다른 특징 없으면 '없음'.
                4. 분석(Analysis): 심리적 배경을 부드럽게.
                5. 질문(Question): 인지 오류면 자기성찰 질문, 긍정 정서 강화면 그 기분을 더 느낄 수 있는 질문.
                6. 대안적 사고(Alternative): 객관적/긍정적 시각, 또는 응원의 말.
                %s
                """.formatted(
                        userNameDisplay,
                        diaryBlock(entries),
                        EmotionStrategies.block(trigger.emotionHint()),
                        EmotionStrategies.DISTORTION_GUIDE,
                        multiDayGuide(entries)
                );
    }

    /** 일기가 1건이면 단일 블록, 여러 건이면 날짜별 흐름 블록. */
    private static String diaryBlock(List<MindDiaryEntry> entries) {
        if (entries.size() == 1) {
            return entrySection("[마음일기 정보]", entries.get(0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[최근 마음일기 흐름] — 총 %d일치, 아래로 갈수록 최신입니다.\n"
                .formatted(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            boolean isTrigger = i == entries.size() - 1;
            String header = isTrigger
                    ? "[%d일차 · 오늘 · 이번 대화의 계기]".formatted(i + 1)
                    : "[%d일차]".formatted(i + 1);
            sb.append('\n').append(entrySection(header, entries.get(i)));
        }
        return sb.toString();
    }

    private static String entrySection(String header, MindDiaryEntry e) {
        String contentDisplay = (e.content() == null || e.content().isBlank())
                ? "(음성으로 기록됨 — 텍스트 변환 없음)"
                : e.content();
        return """
                %s
                - 주제(질문): %s
                - 작성 내용: "%s"
                - 작성 일시: %s
                - 감정 분석: %s
                """.formatted(
                        header,
                        e.question() == null ? "(자유 일기)" : e.question(),
                        contentDisplay,
                        e.recordedAt() == null ? "알 수 없음" : e.recordedAt(),
                        e.emotionDesc() == null ? "(감정 분석 정보 없음)" : e.emotionDesc()
                );
    }

    /** 여러 날치가 주어졌을 때만 붙는 추가 지침. 1건이면 기존 프롬프트와 동일하게 유지된다. */
    private static String multiDayGuide(List<MindDiaryEntry> entries) {
        if (entries.size() == 1) return "";
        return """

                **추가 지침 — 연속된 날들을 하나의 흐름으로 다루세요:**
                - 오늘 하루만 보지 말고, 며칠째 이어지는 감정임을 알아차렸다는 것을 드러내세요.
                - 날짜별로 나열해 요약하지 말고, 반복되는 주제·패턴을 한 줄기로 엮어 말하세요.
                - "며칠째 힘드시네요" 같은 지적이 부담이 되지 않도록, 걱정이 아닌 곁에 있어주는 어조로 전하세요.
                - 왜곡 탐지는 오늘 일기를 기준으로 하되, 이전 날들에서 같은 패턴이 반복되면 그 점을 근거로 삼으세요.
                """;
    }
}
