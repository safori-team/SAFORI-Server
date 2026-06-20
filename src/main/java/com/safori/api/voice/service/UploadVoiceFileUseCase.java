package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceUploadRequest;
import com.safori.api.voice.dto.VoiceUploadResponse;
import com.safori.common.annotation.UseCase;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.service.VoiceDomainService;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;

    public VoiceUploadResponse execute(String username, VoiceUploadRequest request) {
        User user = userAdaptor.queryUserByUsername(username);

        Voice voice = voiceDomainService.uploadVoiceFile(
                user,
                request.getVoiceKey(),
                request.getVoiceTitle(),
                request.getDuration(),
                request.getSampleRate(),
                request.getBitRate());

        return VoiceUploadResponse.builder()
                .voiceId(voice.getId())
                .build();
    }
}
