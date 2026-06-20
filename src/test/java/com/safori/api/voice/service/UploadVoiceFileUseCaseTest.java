package com.safori.api.voice.service;

import com.safori.api.voice.dto.VoiceUploadRequest;
import com.safori.api.voice.dto.VoiceUploadResponse;
import com.safori.domain.user.adaptor.UserAdaptor;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.service.VoiceDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadVoiceFileUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock VoiceDomainService voiceDomainService;
    @Mock User user;
    @Mock Voice voice;

    private VoiceUploadRequest requestOf(String voiceKey) {
        VoiceUploadRequest request = new VoiceUploadRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "voiceKey", voiceKey);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "voiceTitle", "오늘의 마음일기");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "duration", 37);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "sampleRate", 44100);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "bitRate", 128000);
        return request;
    }

    @Test
    @DisplayName("업로드 성공 - 저장된 voiceId 반환")
    void execute_returnsVoiceId() {
        String username = "testUser";
        VoiceUploadRequest request = requestOf("voices/testUser/uuid.m4a");

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(eq(user), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).willReturn(voice);
        given(voice.getId()).willReturn(1L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(userAdaptor, voiceDomainService);

        VoiceUploadResponse response = useCase.execute(username, request);

        assertThat(response.getVoiceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("인증 username으로 조회한 User로 메타데이터 저장")
    void execute_savesWithAuthenticatedUserAndMetadata() {
        String username = "testUser";
        VoiceUploadRequest request = requestOf("voices/testUser/uuid.m4a");

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(any(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt())).willReturn(voice);
        given(voice.getId()).willReturn(42L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(userAdaptor, voiceDomainService);

        useCase.execute(username, request);

        verify(userAdaptor).queryUserByUsername(username);
        verify(voiceDomainService).uploadVoiceFile(user, "voices/testUser/uuid.m4a", "오늘의 마음일기",
                37, 44100, 128000);
    }
}
