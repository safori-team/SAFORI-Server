package com.safori.api.voice.service;

import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteVoiceUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock Voice voice;
    @Mock User user;

    @Test
    @DisplayName("소유자가 삭제 - 성공")
    void execute_owner_deletes() {
        Long voiceId = 1L;
        String username = "owner";
        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getUser()).willReturn(user);
        given(user.getUsername()).willReturn(username);

        DeleteVoiceUseCase useCase = new DeleteVoiceUseCase(voiceAdaptor);
        useCase.execute(voiceId, username);

        verify(voiceAdaptor).deleteById(voiceId);
    }

    @Test
    @DisplayName("타인 소유 삭제 시도 - NO_PERMISSION, 삭제 안 함")
    void execute_notOwner_throws() {
        Long voiceId = 1L;
        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getUser()).willReturn(user);
        given(user.getUsername()).willReturn("owner");

        DeleteVoiceUseCase useCase = new DeleteVoiceUseCase(voiceAdaptor);

        assertThatThrownBy(() -> useCase.execute(voiceId, "attacker"))
                .isInstanceOf(VoiceHandler.class);

        verify(voiceAdaptor, never()).deleteById(voiceId);
    }
}
