package com.safori.domain.emotion.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.emotion.entity.EmotionAnalysisRequest;
import com.safori.domain.emotion.entity.EmotionAnalysisStatus;
import com.safori.domain.emotion.repository.EmotionAnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Adaptor
@RequiredArgsConstructor
public class EmotionAnalysisRequestAdaptorImpl implements EmotionAnalysisRequestAdaptor {

    private final EmotionAnalysisRequestRepository emotionAnalysisRequestRepository;

    @Override
    @Transactional
    public EmotionAnalysisRequest save(EmotionAnalysisRequest request) {
        return emotionAnalysisRequestRepository.save(request);
    }

    @Override
    @Transactional
    public Optional<EmotionAnalysisRequest> queryByRequestIdForUpdate(String requestId) {
        return emotionAnalysisRequestRepository.findByRequestIdForUpdate(requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmotionAnalysisRequest> queryByRequestId(String requestId) {
        return emotionAnalysisRequestRepository.findByRequestId(requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmotionAnalysisRequest> queryPendingCreatedBefore(LocalDateTime threshold) {
        return emotionAnalysisRequestRepository.findTop100ByStatusAndCreatedDateBefore(
                EmotionAnalysisStatus.PENDING, threshold);
    }
}
