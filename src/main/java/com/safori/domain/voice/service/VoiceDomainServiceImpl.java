package com.safori.domain.voice.service;

import com.safori.common.annotation.DomainService;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@DomainService
@Transactional
@RequiredArgsConstructor
public class VoiceDomainServiceImpl implements VoiceDomainService {

    private final VoiceRepository voiceRepository;

    @Override
    public Voice uploadVoiceFile(User user, String voiceKey, String voiceTitle,
                                 int duration, int sampleRate, int bitRate) {
        Voice voice = Voice.builder()
                .user(user)
                .voiceKey(voiceKey)
                .voiceTitle(voiceTitle)
                .duration(duration)
                .sampleRate(sampleRate)
                .bitRate(bitRate)
                .analysisStatus(Voice.AnalysisStatus.PENDING)
                .build();
        return voiceRepository.save(voice);
    }
}
