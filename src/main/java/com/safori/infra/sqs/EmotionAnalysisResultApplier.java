package com.safori.infra.sqs;

import com.safori.domain.emotion.adaptor.EmotionAnalysisRequestAdaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.infra.sqs.dto.EmotionAnalysisResponseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 응답 메시지·타임아웃을 DB에 반영하는 트랜잭션 경계.
 *
 * <p>여기서 예외가 나가면 호출측(폴러)이 메시지를 삭제하지 않아 Visibility Timeout 뒤 재수신된다.
 * 반대로 정상 종료하면 트랜잭션이 커밋된 상태이므로, 완료 이벤트는 이 메서드가 반환된 뒤
 * 발행해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisResultApplier {

    private final EmotionAnalysisRequestAdaptor requestAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceAdaptor voiceAdaptor;
    private final EmotionAnalysisLabelMapper labelMapper;

    /** 응답 처리 결과. 폴러가 ACK 여부와 완료 이벤트 발행 여부를 정하는 데 쓴다. */
    public enum Outcome {
        /** 이번 메시지로 분석이 마감됐다 — 완료 이벤트를 발행한다. */
        SETTLED,
        /** 이미 최종 상태였다(중복 전달) — DB·푸시 재처리 없이 ACK만 한다. */
        DUPLICATE,
        /** 원장에 없는 request_id — 재수신해도 처리할 수 없으므로 ACK한다. */
        UNKNOWN_REQUEST
    }

    /**
     * @param voiceId 마감된 일기 id. 메시지의 {@code clip_id}가 아니라 원장이 알고 있는 값이다
     *                — 후속 푸시가 외부에서 온 식별자를 그대로 믿지 않게 한다.
     */
    public record ApplyResult(Outcome outcome, Long voiceId) {}

    /**
     * 응답 1건을 반영한다.
     *
     * @throws EmotionAnalysisContractException 계약에 없는 {@code processing_status}
     * @throws RuntimeException DB 장애 등 복구 가능한 실패 — 호출측이 ACK하지 않아 재수신된다
     */
    @Transactional
    public ApplyResult apply(EmotionAnalysisResponseMessage message) {
        Optional<EmotionAnalysisRequest> found =
                requestAdaptor.queryByRequestIdForUpdate(message.requestId());
        if (found.isEmpty()) {
            return new ApplyResult(Outcome.UNKNOWN_REQUEST, null);
        }

        EmotionAnalysisRequest request = found.get();
        if (request.isFinalState()) {
            return new ApplyResult(Outcome.DUPLICATE, request.getVoice().getId());
        }

        Voice voice = request.getVoice();
        LocalDateTime completedAt = toLocalDateTime(message.completedAt());

        if (EmotionAnalysisResponseMessage.STATUS_COMPLETED.equals(message.processingStatus())) {
            applyMinorLabels(voice, message);
            request.complete(message.analysisResult(),
                    message.requestKey(), message.responseKey(), completedAt);
        } else if (EmotionAnalysisResponseMessage.STATUS_FAILED.equals(message.processingStatus())) {
            // 소분류 판정만 실패했을 뿐 Gemini 분석 결과는 유효하다 — 그대로 두고 마감한다.
            request.fail(message.analysisResult(),
                    message.requestKey(), message.responseKey(), completedAt);
            log.warn("소분류 감정 분석 실패 응답 — requestId={}, voiceId={} (Gemini 소분류 유지)",
                    request.getRequestId(), voice.getId());
        } else {
            throw new EmotionAnalysisContractException(
                    "Unknown processing_status: " + message.processingStatus());
        }

        // 원장 마감을 변경 감지에 맡기지 않는다. 라벨 교체가 벌크 DELETE를 거치는 경로라
        // 영속성 컨텍스트가 비워지면 이 수정이 조용히 유실된다.
        requestAdaptor.save(request);
        voice.markAnalysisCompleted();
        voiceAdaptor.save(voice);
        return new ApplyResult(Outcome.SETTLED, voice.getId());
    }

    /**
     * 응답이 오지 않은 요청을 마감한다(스윕 전용).
     *
     * @return 이번 호출로 마감한 일기 id. 그 사이 응답이 도착했으면 empty.
     */
    @Transactional
    public Optional<Long> timeOut(String requestId) {
        Optional<EmotionAnalysisRequest> found = requestAdaptor.queryByRequestIdForUpdate(requestId);
        if (found.isEmpty()) return Optional.empty();

        EmotionAnalysisRequest request = found.get();
        if (request.isFinalState()) return Optional.empty();

        request.markTimedOut();
        requestAdaptor.save(request);
        Voice voice = request.getVoice();
        voice.markAnalysisCompleted();
        voiceAdaptor.save(voice);
        return Optional.of(voice.getId());
    }

    /**
     * 소분류 라벨만 통째로 교체한다. 대분류({@code voice_composite})와 전사는 건드리지 않는다.
     * 유효한 라벨이 하나도 없으면 기존 Gemini 소분류를 그대로 둔다 — 빈 화면보다 낫다.
     */
    private void applyMinorLabels(Voice voice, EmotionAnalysisResponseMessage message) {
        List<VoiceEmotionLabel> labels =
                labelMapper.toEmotionLabels(message.analysisResult(), voice);
        if (labels.isEmpty()) {
            log.warn("소분류 응답에 반영할 라벨 없음 — requestId={}, voiceId={} (Gemini 소분류 유지)",
                    message.requestId(), voice.getId());
            return;
        }
        voiceEmotionLabelAdaptor.replaceByVoiceId(voice.getId(), labels);
    }

    /** 응답의 오프셋 시각을 서버 기준 시각으로 맞춘다(스키마 전체가 LocalDateTime). */
    private LocalDateTime toLocalDateTime(OffsetDateTime completedAt) {
        if (completedAt == null) return null;
        return completedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
