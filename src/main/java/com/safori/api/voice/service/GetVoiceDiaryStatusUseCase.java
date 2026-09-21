package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceDiaryStatusResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetVoiceDiaryStatusUseCase {

    private final VoiceAdaptor voiceAdaptor;

    public VoiceDiaryStatusResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }

        return VoiceDiaryStatusResponse.builder()
                .voiceId(voiceId)
                .diaryStatus(voice.getAnalysisStatus())
                .build();
    }
}
