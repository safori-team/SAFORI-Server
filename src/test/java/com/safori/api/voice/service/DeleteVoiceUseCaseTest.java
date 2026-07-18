package com.safori.api.voice.service;

import com.safori.domain.chatbot.service.ChatbotDomainService;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import com.safori.domain.voice.exception.VoiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteVoiceUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock Voice voice;
    @Mock User user;

    @InjectMocks DeleteVoiceUseCase useCase;

    @Test
    @DisplayName("소유자 삭제 - 트리거 세션을 먼저 지운 뒤 일기를 지운다 (순서: 메시지 cascade 정리 후 voice 삭제)")
    void execute_owner_deletesSessionThenVoice() {
        Long voiceId = 1L;
        String username = "owner";
        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getUser()).willReturn(user);
        given(user.getUsername()).willReturn(username);

        useCase.execute(voiceId, username);

        // chat_message.voice_id FK엔 cascade가 없어 순서가 중요하다.
        var order = inOrder(chatbotDomainService, voiceAdaptor);
        order.verify(chatbotDomainService).deleteTriggeredSessionByVoiceId(voiceId);
        order.verify(voiceAdaptor).deleteById(voiceId);
    }

    @Test
    @DisplayName("타인 소유 삭제 시도 - NO_PERMISSION, 세션·일기 모두 삭제 안 함")
    void execute_notOwner_throws() {
        Long voiceId = 1L;
        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getUser()).willReturn(user);
        given(user.getUsername()).willReturn("owner");

        assertThatThrownBy(() -> useCase.execute(voiceId, "attacker"))
                .isInstanceOf(VoiceHandler.class);

        verify(voiceAdaptor, never()).deleteById(voiceId);
        verify(chatbotDomainService, never()).deleteTriggeredSessionByVoiceId(voiceId);
    }
}
