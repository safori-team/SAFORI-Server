package com.safori.api.chatbot.controller;

import com.safori.api.chatbot.dto.ChatHistoryResponse;
import com.safori.api.chatbot.dto.CreateSessionResponse;
import com.safori.api.chatbot.dto.FeedbackRequest;
import com.safori.api.chatbot.dto.FeedbackResponse;
import com.safori.api.chatbot.dto.ReframingRequest;
import com.safori.api.chatbot.dto.ReframingResponse;
import com.safori.api.chatbot.dto.ChatSessionItemResponse;
import com.safori.api.chatbot.dto.MindDiaryOfferResponse;
import com.safori.api.chatbot.dto.VoicePlaybackResponse;
import com.safori.api.chatbot.dto.VoiceReframingRequest;
import com.safori.api.chatbot.dto.VoiceReframingResponse;
import com.safori.api.common.dto.PagedResponse;
import com.safori.api.chatbot.service.CreateChatSessionUseCase;
import com.safori.api.chatbot.service.DeleteChatSessionUseCase;
import com.safori.api.chatbot.service.GetChatHistoryUseCase;
import com.safori.api.chatbot.service.GetChatSessionsUseCase;
import com.safori.api.chatbot.service.GetChatVoicePlaybackUrlUseCase;
import com.safori.api.chatbot.service.GetMindDiaryOfferUseCase;
import com.safori.api.chatbot.service.AcceptMindDiaryOfferUseCase;
import com.safori.api.chatbot.service.DeclineMindDiaryOfferUseCase;
import com.safori.api.chatbot.service.SendReframingMessageUseCase;
import com.safori.api.chatbot.service.SendVoiceReframingMessageUseCase;
import com.safori.api.chatbot.service.UpdateMessageFeedbackUseCase;
import com.safori.api.common.dto.ApiResponseDto;
import com.safori.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[챗봇 - 도란이]",
     description = """
             CBT 리프레이밍 챗봇 '도란이' API.

             전형적 사용 흐름:
               1) POST /sessions  → 새 채팅방을 만들고 sessionId(UUID v4) 받기
               2-a) [텍스트] POST /reframing  → sessionId + userInput으로 도란이 응답 즉시 반환
               2-b) [음성]  GET /v1/api/voices/presigned-url → S3 PUT → POST /voice-reframing(sessionId+voiceKey)
                            → 서버가 STT+감정분석+상담을 한 번에 처리, content(STT)와 도란이 응답을 함께 반환
               3) PUT /messages/{messageId}/feedback  → 사용자가 봇 분석에 대해 '진짜 마음' 피드백
               4) GET /sessions, /history/{sessionId} → 채팅방 목록·상세 화면
               5) DELETE /sessions/{sessionId}  → 채팅방 삭제 (cascade hard delete)

             마음일기 상담 제안(모달 opt-in):
               마음일기가 조건(예: 3일 연속 부정 감정)을 충족하면 서버가 상담 '제안'을 만든다.
               세션은 이때 만들어지지 않는다.
                 1) GET  /mind-diary-offer            → 대기 중 제안 확인 (있으면 모달 표시)
                 2) POST /mind-diary-offer/{id}/accept → 수락 시 세션 생성 + 도란이 첫 메시지
                    POST /mind-diary-offer/{id}/decline → 거절 (재제안 없음)
             """)
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/v1/api/chatbot")
public class ChatbotApiController {

    private final CreateChatSessionUseCase createChatSessionUseCase;
    private final DeleteChatSessionUseCase deleteChatSessionUseCase;
    private final GetChatSessionsUseCase getChatSessionsUseCase;
    private final GetChatHistoryUseCase getChatHistoryUseCase;
    private final SendReframingMessageUseCase sendReframingMessageUseCase;
    private final SendVoiceReframingMessageUseCase sendVoiceReframingMessageUseCase;
    private final UpdateMessageFeedbackUseCase updateMessageFeedbackUseCase;
    private final GetChatVoicePlaybackUrlUseCase getChatVoicePlaybackUrlUseCase;
    private final GetMindDiaryOfferUseCase getMindDiaryOfferUseCase;
    private final AcceptMindDiaryOfferUseCase acceptMindDiaryOfferUseCase;
    private final DeclineMindDiaryOfferUseCase declineMindDiaryOfferUseCase;

    @PostMapping("/sessions")
    @Operation(
            summary = "채팅 세션 생성",
            description = """
                    새 채팅방을 시작할 때 호출.
                    응답 result.sessionId(UUID v4)를 저장해 두고 이후 메시지 전송에 사용한다.
                    body 불필요, 인증 헤더만 있으면 된다.
                    """)
    public ApiResponseDto<CreateSessionResponse> createSession(@UserCode String username) {
        return ApiResponseDto.onSuccess(createChatSessionUseCase.execute(username));
    }

    @GetMapping("/mind-diary-offer")
    @Operation(
            summary = "마음일기 상담 제안 조회 (모달용)",
            description = """
                    조건(예: 3일 연속 부정 감정)을 충족해 대기 중인 상담 제안이 있는지 조회한다.
                    앱 진입/홈 화면에서 호출해, hasOffer=true면 "상담을 받아보시겠어요?" 모달을 띄운다.

                    응답:
                      • hasOffer  - 대기 중 제안 존재 여부
                      • offerId   - 수락/거절 호출에 쓰는 ID (없으면 null)
                      • reason    - 제안 근거 코드 (없으면 null)
                      • offeredAt - 제안 감지 시각 (없으면 null)

                    제안이 여러 개면 가장 최근 것을 반환한다.
                    """)
    public ApiResponseDto<MindDiaryOfferResponse> getMindDiaryOffer(@UserCode String username) {
        return ApiResponseDto.onSuccess(getMindDiaryOfferUseCase.execute(username));
    }

    @PostMapping("/mind-diary-offer/{offerId}/accept")
    @Operation(
            summary = "마음일기 상담 제안 수락 → 세션 생성",
            description = """
                    모달에서 사용자가 상담을 수락하면 호출. 서버가 이때 비로소 세션을 만들고
                    도란이 첫 메시지(Gemini)를 생성한다. 응답 sessionId로 채팅 화면에 진입한다.

                    수락 시점에 조건을 다시 확인한다. 제안 이후 감정 신고·재분석 등으로 조건이
                    깨졌으면 4210 CHAT_OFFER_EXPIRED로 거절된다.

                    오류:
                      • 4207 CHAT_OFFER_NOT_FOUND      - 존재하지 않는 offerId
                      • 4208 CHAT_OFFER_NO_PERMISSION  - 다른 사용자의 제안
                      • 4209 CHAT_OFFER_NOT_OFFERABLE  - 이미 수락/거절한 제안
                      • 4210 CHAT_OFFER_EXPIRED        - 조건이 더 이상 유효하지 않음
                    """)
    public ApiResponseDto<CreateSessionResponse> acceptMindDiaryOffer(
            @UserCode String username,
            @PathVariable Long offerId
    ) {
        return ApiResponseDto.onSuccess(acceptMindDiaryOfferUseCase.execute(username, offerId));
    }

    @PostMapping("/mind-diary-offer/{offerId}/decline")
    @Operation(
            summary = "마음일기 상담 제안 거절",
            description = """
                    모달에서 사용자가 상담을 원치 않으면 호출. 제안이 DECLINED로 바뀌고
                    같은 일기로는 다시 제안하지 않는다. 세션은 만들어지지 않는다.

                    오류:
                      • 4207 CHAT_OFFER_NOT_FOUND      - 존재하지 않는 offerId
                      • 4208 CHAT_OFFER_NO_PERMISSION  - 다른 사용자의 제안
                      • 4209 CHAT_OFFER_NOT_OFFERABLE  - 이미 수락/거절한 제안
                    """)
    public ApiResponseDto<Void> declineMindDiaryOffer(
            @UserCode String username,
            @PathVariable Long offerId
    ) {
        declineMindDiaryOfferUseCase.execute(username, offerId);
        return ApiResponseDto.onSuccess(null);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(
            summary = "채팅 세션 삭제 (hard delete, cascade)",
            description = """
                    채팅방 삭제. 해당 세션에 속한 모든 메시지가 즉시 영구 삭제된다(복구 불가).
                    UI에서는 삭제 확인 다이얼로그를 띄우는 것을 권장.

                    오류:
                      • 4200 CHAT_SESSION_NOT_FOUND  - 존재하지 않는 sessionId
                      • 4201 CHAT_SESSION_NO_PERMISSION  - 다른 사용자의 세션
                    """)
    public ApiResponseDto<Void> deleteSession(
            @UserCode String username,
            @PathVariable String sessionId
    ) {
        deleteChatSessionUseCase.execute(username, sessionId);
        return ApiResponseDto.onSuccess(null);
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "내 채팅방 목록 조회 (최신 활동순, 페이징)",
            description = """
                    홈/채팅 목록 화면에서 사용. 사용자의 세션을 마지막 메시지 시각 기준 내림차순으로 반환.

                    페이징:
                      • page (1-based, default 1)
                      • size (default 20, max 100)

                    응답은 표준 PagedResponse 형상:
                      { items, page, size, totalElements, totalPages, hasNext }

                    각 세션 미리보기 항목:
                      • lastMessage    - 마지막 사용자 발화 (마음일기 트리거 세션이면 null)
                      • lastUpdated    - 세션 lastModifiedDate
                      • distortionTags - 마지막 메시지의 detected_distortion ('없음'이면 빈 배열)
                      • emotion        - 사용자 피드백(feedback_emotion) > 봇 분석(emotion) 우선
                    """)
    public ApiResponseDto<PagedResponse<ChatSessionItemResponse>> getSessions(
            @UserCode String username,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponseDto.onSuccess(getChatSessionsUseCase.execute(username, page, size));
    }

    @GetMapping("/history/{sessionId}")
    @Operation(
            summary = "채팅 상세 (메시지 페이징)",
            description = """
                    특정 세션의 대화 내용을 페이지 단위로 시간순 반환.

                    페이징:
                      • page (1-based, default 1)
                      • size (default 20, max 100)

                    응답: sessionId + 표준 PagedResponse 필드(items, page, size, totalElements, totalPages, hasNext).

                    응답 구조 주의:
                      • DB row 1개 = user 발화 + assistant 응답 한 쌍이 펼쳐져 items 배열에 두 항목으로 노출됨
                      • 같은 한 쌍은 동일한 messageId를 공유 (assistant 행에 피드백 PUT을 쏘면 됨)
                      • 마음일기 트리거(MIND_DIARY) 메시지는 user 발화 없이 assistant 항목만 포함

                    assistant 항목의 도란이 응답 6개 필드 (UI 매핑 가이드):
                      • detected_distortion  → 상단 분류 뱃지 (흑백사고/파국화/긍정 정서 강화 등 11개)
                      • empathy              → 본문 공감 멘트
                      • socratic_question    → '함께 생각해봐요' 카드
                      • analysis             → 'AI 분석 리포트 - 심층 분석' 카드
                      • alternative_thought  → '대안적 사고' 카드
                      • emotion              → 사이드 감정 라벨 (happy/sad/neutral/angry/anxiety/surprise)

                    피드백 필드 (사용자가 PUT 호출 후에만 채워짐):
                      • feedbackEmotion / feedbackDetail / feedbackAt
                    """)
    public ApiResponseDto<ChatHistoryResponse> getHistory(
            @UserCode String username,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponseDto.onSuccess(getChatHistoryUseCase.execute(username, sessionId, page, size));
    }

    @PostMapping("/reframing")
    @Operation(
            summary = "텍스트 채팅 (도란이 동기 응답)",
            description = """
                    텍스트로 도란이와 대화. 서버가 LLM(Gemini 2.5 Pro)을 동기로 호출하므로
                    응답까지 보통 3~10초 소요됨. 클라이언트 타임아웃은 30초 이상 권장.

                    body:
                      • sessionId  (필수) - 미리 만든 세션 UUID
                      • userInput  (필수) - 사용자 발화
                      • emotion    (선택) - 클라이언트가 짐작한 감정 힌트
                                           (happy/sad/neutral/angry/anxiety/surprise)

                    응답: assistant 6개 필드 + 새로 저장된 messageId (피드백 PUT에 사용 가능)
                          + sessionClosed / crisisDetected / crisisTrigger.

                    대화 턴 제한 (CBT 상담은 정해진 분량 안에서 끝난다):
                      • 세션당 사용자 발화 4회. 4번째 발화의 응답이 도란이의 마무리 멘트다.
                      • 마무리 응답은 sessionClosed=true로 오고, socraticQuestion에 질문 대신 마무리 말이 담긴다.
                        (응답 형상은 동일하니 렌더링 분기는 필요 없음. 입력창만 비활성화하면 됨)
                      • 닫힌 세션에 또 보내면 4206 CHAT_SESSION_CLOSED.
                      • 마음일기 세션은 도란이 첫 질문으로 시작하므로 (질문 → 응답)이 4번 오간다.

                    위기 가드레일 (턴 제한과 같은 방식으로 상담을 멈춘다):
                      자·타해 의도가 감지되면 남은 턴과 무관하게 상담이 즉시 종료된다.
                      상담이 아니라 치료가 필요한 상태에서 CBT 질문을 계속 던지지 않기 위해서다.
                      • 응답은 sessionClosed=true + crisisDetected=true로 온다.
                      • 6개 필드는 CBT 상담 대신 안전 안내로 채워진다
                        (detectedDistortion="위기 상황", emotion="anxiety",
                         analysis에 자살예방 상담전화 109 안내). 응답 형상은 동일.
                      • crisisTrigger로 발동 원인이 온다:
                          HIGH_RISK_KEYWORD  - 발화에 고위험 표현이 직접 담김 (LLM 호출 전 차단)
                          AI_CRISIS_CLASSIFIER - 문맥 분류기가 현재의 적극적 자·타해 위험을 판정
                          CRISIS_DISTORTION  - 모델이 스스로 '위기 상황'으로 판정
                      • 이후 이 세션에 보내면 4212 CHAT_SESSION_CRISIS_CLOSED. 다시 열리지 않는다.
                      • 세션 목록/상세 조회에도 crisisDetected가 실려 오므로,
                        채팅방을 다시 열었을 때 안내 배너를 유지할 수 있다.
                      • UI 권장: 입력창 비활성화 + 109 전화 걸기 버튼 노출.

                    Gemini 호출 실패 시 폴백 응답 ("죄송해요, 잠시 생각이 꼬였나 봐요...") 반환.
                    """)
    public ApiResponseDto<ReframingResponse> reframing(
            @UserCode String username,
            @RequestBody @Valid ReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendReframingMessageUseCase.execute(username, request));
    }

    @PostMapping("/voice-reframing")
    @Operation(
            summary = "음성 채팅 (STT + 감정분석 + 상담을 한 번에)",
            description = """
                    음성 입력으로 도란이와 대화. 한 번의 호출로 서버가 전체 파이프라인을 처리한다:
                      voiceKey → Gemini Flash(STT + 감정분석) → Gemini Pro(상담) → 메시지 저장 → 응답

                    선행 단계 (마음일기와 동일하게 재사용):
                      1) GET /v1/api/voices/presigned-url?extension=m4a → presignedUrl·voiceKey
                      2) PUT {presignedUrl} (S3 직접 업로드)

                    마음일기 등록 API와 분리된 경로로, VoiceAnalysisCompletedEvent를 발행하지 않아
                    의도치 않은 채팅 세션 자동 생성 부수효과가 없다.

                    Flash + Pro 2단계를 동기로 호출하므로 보통 6~20초 소요됨.
                    클라이언트 타임아웃은 30초 이상 권장. (UI는 '전송 중...' 후 응답 도착 시
                    사용자 발화 말풍선(content)과 도란이 답변을 함께 렌더)

                    body:
                      • sessionId (필수) - 미리 만든 세션 UUID
                      • voiceKey  (필수) - presigned-url로 S3 업로드 완료한 음성 키

                    응답:
                      • messageId - 저장된 메시지 ID
                      • content   - STT 전사 텍스트 (사용자 발화 말풍선)
                      • 도란이 6개 필드 (empathy, detectedDistortion, analysis, socraticQuestion,
                                        alternativeThought, emotion)
                      • sessionClosed / crisisDetected / crisisTrigger

                    위기 가드레일:
                      텍스트 경로(POST /reframing)와 동일하게 동작한다. 다만 사전 스크리닝이
                      STT 이후에 돌기 때문에, 걸리면 Flash는 이미 쓰였고 Pro 호출만 건너뛴다.
                      content(STT 전사 텍스트)는 정상적으로 채워지므로 사용자 발화 말풍선은 그대로 그린다.

                    Pro 호출 실패 시 폴백 응답("죄송해요, 잠시 생각이 꼬였나 봐요...") 반환.

                    오류:
                      • 4205 CHAT_VOICE_STT_FAILED       - STT/감정 분석(Flash) 실패
                      • 4212 CHAT_SESSION_CRISIS_CLOSED  - 위기 가드레일로 종료된 세션
                    """)
    public ApiResponseDto<VoiceReframingResponse> voiceReframing(
            @UserCode String username,
            @RequestBody @Valid VoiceReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendVoiceReframingMessageUseCase.execute(username, request));
    }

    @GetMapping("/voices/playback-url")
    @Operation(
            summary = "음성 발화 재생용 URL 발급",
            description = """
                    채팅 히스토리의 사용자 음성 발화(USER_VOICE)를 다시 듣기 위한 presigned GET URL을 발급한다.
                    히스토리 항목의 voiceKey를 그대로 전달하면 되며, 사용자가 재생을 누른 시점에만 호출한다
                    (목록 전체에 URL을 싣지 않음).

                    응답 url은 유효시간 1시간. 본인이 업로드한 voiceKey만 발급 가능.

                    오류:
                      • 4151 VOICE_NO_PERMISSION - 본인 소유가 아닌 voiceKey
                    """)
    public ApiResponseDto<VoicePlaybackResponse> getVoicePlaybackUrl(
            @UserCode String username,
            @RequestParam String voiceKey
    ) {
        return ApiResponseDto.onSuccess(getChatVoicePlaybackUrlUseCase.execute(username, voiceKey));
    }

    @PutMapping("/messages/{messageId}/feedback")
    @Operation(
            summary = "메시지에 '진짜 마음' 피드백 기록",
            description = """
                    봇이 분석한 emotion이 본인 마음과 다를 때 사용자가 직접 입력하는 피드백.
                    원본 bot_response.emotion은 그대로 유지되고, feedback_emotion 컬럼이 별도 저장됨
                    (분석용 데이터로 둘 다 보존).

                    GET /sessions / GET /history 응답에서는 feedback_emotion이 있으면 해당 값을 우선 노출.

                    body:
                      • emotion (필수) - happy / sad / neutral / angry / anxiety / surprise 중 하나
                                          (그 외 값이면 4204 CHAT_FEEDBACK_INVALID_EMOTION)
                      • detail  (선택) - "자세한 이야기" 자유 입력

                    같은 messageId에 다시 호출하면 마지막 값으로 덮어쓰기 + feedback_at 갱신 (수정 가능).

                    오류:
                      • 4202 CHAT_MESSAGE_NOT_FOUND
                      • 4203 CHAT_MESSAGE_NO_PERMISSION  - 다른 사용자 세션의 메시지
                      • 4204 CHAT_FEEDBACK_INVALID_EMOTION
                    """)
    public ApiResponseDto<FeedbackResponse> updateFeedback(
            @UserCode String username,
            @PathVariable Long messageId,
            @RequestBody @Valid FeedbackRequest request
    ) {
        return ApiResponseDto.onSuccess(
                updateMessageFeedbackUseCase.execute(username, messageId, request));
    }
}
