package com.safori.domain.emotion.adaptor;

import com.safori.domain.emotion.entity.EmotionAnalysisRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisRequestAdaptor {

    EmotionAnalysisRequest save(EmotionAnalysisRequest request);

    /** 응답 반영용 조회 (행 잠금). 호출측 트랜잭션 안에서 쓴다. */
    Optional<EmotionAnalysisRequest> queryByRequestIdForUpdate(String requestId);

    Optional<EmotionAnalysisRequest> queryByRequestId(String requestId);

    /** {@code threshold} 이전에 생성된 PENDING 요청 (최대 100건). */
    List<EmotionAnalysisRequest> queryPendingCreatedBefore(LocalDateTime threshold);
}
