package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.entity.VoiceEmotionLabel;
import com.safori.domain.voice.repository.VoiceEmotionLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Adaptor
@RequiredArgsConstructor
public class VoiceEmotionLabelAdaptorImpl implements VoiceEmotionLabelAdaptor {

    private final VoiceEmotionLabelRepository voiceEmotionLabelRepository;

    @Override
    @Transactional
    public List<VoiceEmotionLabel> saveAll(List<VoiceEmotionLabel> labels) {
        return voiceEmotionLabelRepository.saveAll(labels);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceEmotionLabel> findByVoiceId(Long voiceId) {
        return voiceEmotionLabelRepository.findByVoice_Id(voiceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceEmotionLabel> findByVoiceIds(List<Long> voiceIds) {
        if (voiceIds == null || voiceIds.isEmpty()) return List.of();
        return voiceEmotionLabelRepository.findByVoice_IdIn(voiceIds);
    }

    @Override
    @Transactional
    public void deleteByVoiceId(Long voiceId) {
        voiceEmotionLabelRepository.deleteByVoice_Id(voiceId);
    }

    @Override
    @Transactional
    public List<VoiceEmotionLabel> replaceByVoiceId(Long voiceId, List<VoiceEmotionLabel> labels) {
        voiceEmotionLabelRepository.deleteAllByVoiceIdInBulk(voiceId);
        if (labels == null || labels.isEmpty()) return List.of();
        return voiceEmotionLabelRepository.saveAll(labels);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> findMonthlyLabelStats(String username, int year, int month) {
        return voiceEmotionLabelRepository.findMonthlyLabelStats(username, year, month);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Voice> findVoicesByLabel(String username, String label, int year, int month) {
        return voiceEmotionLabelRepository.findVoicesByLabel(username, label, year, month);
    }
}
