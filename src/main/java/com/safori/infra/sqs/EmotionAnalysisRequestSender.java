package com.safori.infra.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safori.domain.emotion.adaptor.EmotionAnalysisRequestAdaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.emotion.entity.EmotionAnalysisStatus;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.Voice;
import com.safori.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.safori.infra.sqs.config.EmotionAnalysisSqsProperties;
import com.safori.infra.sqs.dto.EmotionAnalysisRequestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Gemini 1차 분석 결과를 요청 큐로 넘긴다.
 *
 * <p>원장 행을 <b>먼저 커밋한 뒤</b> 전송한다. 순서가 뒤바뀌면 분석이 빨리 끝났을 때 응답이
 * 원장보다 먼저 도착해 매칭할 대상이 없어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisRequestSender {

    private final Optional<SqsClient> sqsClient;
    private final EmotionAnalysisSqsProperties properties;
    private final EmotionAnalysisRequestAdaptor requestAdaptor;
    private final EmotionAnalysisPayloadMapper payloadMapper;
    private final ObjectMapper objectMapper;

    /** 큐가 설정돼 있고 클라이언트가 떠 있는지. 비활성이면 호출측이 Gemini 소분류로 마감한다. */
    public boolean isEnabled() {
        return sqsClient.isPresent() && properties.sendEnabled();
    }

    /**
     * 소분류 분석을 요청한다.
     *
     * @param major Gemini가 확정한 대분류. 계약상 필수이며 빠지면 Lambda가 4xx로 거절한다.
     * @return 큐로 넘어갔으면 true — 분석 완료 처리는 응답 수신 시점으로 미룬다.
     *         false면 호출측이 Gemini 소분류로 즉시 마감해야 한다.
     */
    public boolean handOff(Voice voice, GeminiAnalysisResult result, EmotionType major) {
        if (!isEnabled()) return false;

        String requestId = UUID.randomUUID().toString();
        EmotionAnalysisRequest request;
        try {
            request = requestAdaptor.save(EmotionAnalysisRequest.builder()
                    .requestId(requestId)
                    .voice(voice)
                    .status(EmotionAnalysisStatus.PENDING)
                    .build());
        } catch (Exception e) {
            // 원장을 못 남기면 응답을 매칭할 수 없다 — 보내지 않고 Gemini 소분류로 마감시킨다.
            log.error("소분류 감정 분석 요청 원장 저장 실패 — voiceId={}", voice.getId(), e);
            return false;
        }

        try {
            String body = objectMapper.writeValueAsString(new EmotionAnalysisRequestMessage(
                    requestId,
                    voice.getUser().getId(),
                    voice.getId(),
                    // Wrapper가 Python fromisoformat으로 파싱한다. 3.10 이하는 소수 자릿수가
                    // 3·6일 때만 읽으므로 초 단위로 자른다 — 어느 런타임에서도 파싱된다.
                    OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    payloadMapper.toGeminiResultPayload(result, major)));

            sqsClient.get().sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.getRequestQueueUrl())
                    .messageBody(body)
                    .build());

            log.info("소분류 감정 분석 요청 전송 — requestId={}, voiceId={}", requestId, voice.getId());
            return true;
        } catch (Exception e) {
            // 전송만 실패했으므로 Gemini 분석 결과는 그대로 살린다.
            log.error("소분류 감정 분석 요청 전송 실패 — requestId={}, voiceId={}",
                    requestId, voice.getId(), e);
            try {
                request.markSendFailed();
                requestAdaptor.save(request);
            } catch (Exception ex) {
                log.error("요청 상태 SEND_FAILED 기록 실패 — requestId={}", requestId, ex);
            }
            return false;
        }
    }
}
