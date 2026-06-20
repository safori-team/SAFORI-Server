package com.safori.api.voice.service;

import com.safori.common.annotation.UseCase;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import lombok.RequiredArgsConstructor;

/**
 * 음성(마음일기) 삭제.
 * 소유권 확인 후 Voice 레코드를 삭제한다.
 */
@UseCase
@RequiredArgsConstructor
public class DeleteVoiceUseCase {

    private final VoiceAdaptor voiceAdaptor;

    public void execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) {
            throw VoiceHandler.NO_PERMISSION;
        }
        voiceAdaptor.deleteById(voiceId);
    }
}
