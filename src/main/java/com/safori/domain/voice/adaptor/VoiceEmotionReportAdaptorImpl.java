package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.emotion.entity.EmotionType;
import com.safori.domain.voice.entity.VoiceEmotionReport;
import com.safori.domain.voice.repository.VoiceEmotionReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Adaptor
@RequiredArgsConstructor
public class VoiceEmotionReportAdaptorImpl implements VoiceEmotionReportAdaptor {

    private final VoiceEmotionReportRepository voiceEmotionReportRepository;

    @Override
    @Transactional
    public VoiceEmotionReport save(VoiceEmotionReport report) {
        return voiceEmotionReportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VoiceEmotionReport> findByVoiceIdAndUsername(Long voiceId, String username) {
        return voiceEmotionReportRepository.findByVoice_IdAndUser_Username(voiceId, username);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EmotionType> findReportedEmotions(List<Long> voiceIds, String username) {
        if (voiceIds.isEmpty()) {
            return Map.of();
        }
        return voiceEmotionReportRepository.findByVoice_IdInAndUser_Username(voiceIds, username).stream()
                .collect(Collectors.toMap(r -> r.getVoice().getId(), VoiceEmotionReport::getReportedEmotion));
    }
}
