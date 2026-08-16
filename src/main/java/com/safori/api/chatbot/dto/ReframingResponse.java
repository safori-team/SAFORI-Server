package com.safori.api.chatbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인지 재구성 응답")
public record ReframingResponse(
        @Schema(description = "메시지 ID", example = "101")
        Long messageId,
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
                (보내면 4206 CHAT_SESSION_CLOSED, 위기 종료면 4212 CHAT_SESSION_CRISIS_CLOSED),
                socraticQuestion에는 질문 대신 마무리 말이 담긴다.
                입력창을 비활성화하고 새 상담을 안내하면 된다.

                턴 소진(4회)과 위기 가드레일 두 경로 모두 이 값이 true다.
                둘을 구분하려면 crisisDetected를 함께 본다.""", example = "false")
        boolean sessionClosed,
        @Schema(description = """
                위기 가드레일이 발동해 상담이 중단됐는지. true면 sessionClosed도 항상 true다.

                이 경우 응답 6개 필드는 CBT 상담 내용이 아니라 안전 안내로 채워진다
                (detectedDistortion="위기 상황", emotion="anxiety", analysis에 자살예방 상담전화 109 안내).
                응답 형상은 동일하므로 렌더링 분기는 필요 없지만, 전문기관 연결 배너·전화 걸기 버튼을
                띄우려면 이 플래그를 쓴다. 남은 턴이 있어도 세션은 다시 열리지 않는다.""",
                example = "false")
        boolean crisisDetected,
        @Schema(description = """
                가드레일 발동 원인 (crisisDetected=false면 null).
                  • HIGH_RISK_KEYWORD  - 발화에 고위험 표현이 직접 담김 (LLM 호출 전 차단)
                  • AI_CRISIS_CLASSIFIER - 문맥 분류기가 현재의 적극적 자·타해 위험을 판정
                  • CRISIS_DISTORTION  - 모델이 스스로 '위기 상황'으로 판정
                UI 분기용이 아니라 로깅·분석용 값이다. 화면 처리는 crisisDetected만 보면 된다.""",
                example = "HIGH_RISK_KEYWORD")
        String crisisTrigger
) {}
