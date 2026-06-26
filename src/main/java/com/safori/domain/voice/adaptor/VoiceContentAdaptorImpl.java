package com.safori.domain.voice.adaptor;

import com.safori.common.annotation.Adaptor;
import com.safori.domain.voice.entity.VoiceContent;
import com.safori.domain.voice.repository.VoiceContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Adaptor
@RequiredArgsConstructor
public class VoiceContentAdaptorImpl implements VoiceContentAdaptor {

    private final VoiceContentRepository voiceContentRepository;

    @Override
    @Transactional
    public VoiceContent save(VoiceContent voiceContent) {
        return voiceContentRepository.save(voiceContent);
    }
}
