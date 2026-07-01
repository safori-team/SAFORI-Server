package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.voice.entity.VoiceComposite;
import com.safori.domain.voice.repository.VoiceCompositeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Adaptor
@RequiredArgsConstructor
public class VoiceCompositeAdaptorImpl implements VoiceCompositeAdaptor {

    private final VoiceCompositeRepository voiceCompositeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end) {
        return voiceCompositeRepository.findByVoice_User_UsernameAndCreatedDateBetween(username, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds) {
        if (voiceIds.isEmpty()) {
            return List.of();
        }
        return voiceCompositeRepository.findByVoice_IdIn(voiceIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VoiceComposite> findByVoiceId(Long voiceId) {
        return voiceCompositeRepository.findByVoice_Id(voiceId);
    }

    @Override
    @Transactional
    public VoiceComposite save(VoiceComposite voiceComposite) {
        return voiceCompositeRepository.save(voiceComposite);
    }

    @Override
    @Transactional
    public void deleteByVoiceId(Long voiceId) {
        voiceCompositeRepository.deleteByVoice_Id(voiceId);
    }
}
