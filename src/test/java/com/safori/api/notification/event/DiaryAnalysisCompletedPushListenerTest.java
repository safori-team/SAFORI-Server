package com.safori.api.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.safori.api.notification.port.PushMessage;
import com.safori.api.notification.service.SendPushNotificationUseCase;
import com.safori.common.consts.NotificationStaticValues;
import com.safori.common.event.VoiceAnalysisCompletedEvent;
import com.safori.domain.user.entity.User;
import com.safori.domain.voice.adaptor.VoiceAdaptor;
import com.safori.domain.voice.entity.Voice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryAnalysisCompletedPushListenerTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock SendPushNotificationUseCase sendPushNotificationUseCase;

    @InjectMocks DiaryAnalysisCompletedPushListener listener;

    @Test
    @DisplayName("분석 완료 시 소유자에게 분석완료 푸시(voiceId 딥링크)를 보낸다")
    void sendsPushToOwner() {
        User owner = User.builder().id(7L).username("u7").build();
        Voice voice = Voice.builder().id(42L).voiceKey("k").user(owner).build();
        given(voiceAdaptor.queryById(42L)).willReturn(voice);

        listener.onDiaryAnalysisCompleted(new VoiceAnalysisCompletedEvent(42L));

        ArgumentCaptor<PushMessage> msg = ArgumentCaptor.forClass(PushMessage.class);
        verify(sendPushNotificationUseCase).execute(eq(7L), msg.capture());
        assertThat(msg.getValue().data())
                .containsEntry(NotificationStaticValues.DATA_TYPE, NotificationStaticValues.TYPE_DIARY_ANALYSIS_DONE)
                .containsEntry(NotificationStaticValues.KEY_VOICE_ID, "42");
    }

    @Test
    @DisplayName("조회 실패해도 예외를 삼켜 분석 흐름에 영향 없다")
    void swallowsExceptions() {
        given(voiceAdaptor.queryById(any())).willThrow(new RuntimeException("boom"));

        assertThatCode(() -> listener.onDiaryAnalysisCompleted(new VoiceAnalysisCompletedEvent(1L)))
                .doesNotThrowAnyException();
        verify(sendPushNotificationUseCase, never()).execute(any(), any());
    }
}
