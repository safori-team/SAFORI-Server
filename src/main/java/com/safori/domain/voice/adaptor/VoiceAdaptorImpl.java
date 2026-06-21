package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import static com.safori.domain.voice.exception.VoiceHandler.NOT_FOUND;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VoiceAdaptorImpl implements VoiceAdaptor {

    private final VoiceRepository voiceRepository;

    @Override
    public Voice queryById(Long voiceId) {
        return voiceRepository.findById(voiceId)
                .orElseThrow(() -> NOT_FOUND);
    }

    @Override
    @Transactional
    public void deleteById(Long voiceId) {
        voiceRepository.deleteById(voiceId);
    }
}
